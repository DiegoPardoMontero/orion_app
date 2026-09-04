package co.orion.engagement.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.engagement.domain.Achievement;
import co.orion.engagement.domain.AchievementEvaluators;
import co.orion.engagement.domain.AchievementInput;
import co.orion.engagement.domain.AchievementUnlockedEvent;
import co.orion.engagement.domain.StreakCalculator;
import co.orion.engagement.domain.StreakProtection;
import co.orion.engagement.domain.UserAchievement;
import co.orion.engagement.persistence.AchievementRepository;
import co.orion.engagement.persistence.PointEventRepository;
import co.orion.engagement.persistence.StreakProtectionRepository;
import co.orion.engagement.persistence.UserAchievementRepository;
import co.orion.identity.application.StudentProfileService;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.StudentGoalRepository;
import co.orion.messaging.persistence.MessageRepository;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.LearningProgress;
import co.orion.scheduling.domain.LearningProgress.Tomada;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;

/**
 * El motor de logros. Reevalúa los veinte, guarda el progreso, enciende lo que toque y escribe los
 * puntos.
 *
 * <p>Es el corazón del bloque y su propiedad más importante es esta: <strong>reevaluar produce
 * siempre el mismo estado</strong>. No hay contadores incrementales que puedan desincronizarse —
 * cada evaluación parte de una foto completa del estudiante y el índice único de {@code
 * point_events} impide conceder dos veces lo mismo. Por eso {@code recompute} y el procesamiento
 * incremental coinciden, y por eso reprocesar un evento reenviado no duplica nada.
 *
 * <p>{@code engagement} no llama a nadie: escucha. Este servicio lo invocan los listeners.
 */
@Service
public class AchievementService {

    /** Puntos por clase completada, del brief. Los del logro salen del catálogo. */
    private static final int PUNTOS_POR_CLASE = 25;
    private static final int PUNTOS_POR_RESENA = 20;

    private static final String FUENTE_CLASE = "LESSON";
    private static final String FUENTE_RESENA = "REVIEW";
    private static final String FUENTE_LOGRO = "ACHIEVEMENT";

    private static final String AJUSTE_GRATUITAS = "gamification_count_free_lessons";

    private final BookingRepository bookings;
    private final ProfessorProfileRepository professorProfiles;
    private final StudentProfileService studentProfiles;
    private final StudentGoalRepository studentGoals;
    private final MessageRepository messages;
    private final ReviewRepository reviews;
    private final AchievementRepository achievements;
    private final UserAchievementRepository userAchievements;
    private final PointEventRepository pointEvents;
    private final StreakProtectionRepository protections;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AchievementService(BookingRepository bookings,
                              ProfessorProfileRepository professorProfiles,
                              StudentProfileService studentProfiles,
                              StudentGoalRepository studentGoals,
                              MessageRepository messages,
                              ReviewRepository reviews,
                              AchievementRepository achievements,
                              UserAchievementRepository userAchievements,
                              PointEventRepository pointEvents,
                              StreakProtectionRepository protections,
                              PlatformSettingsService settings,
                              ApplicationEventPublisher events,
                              Clock clock) {
        this.bookings = bookings;
        this.professorProfiles = professorProfiles;
        this.studentProfiles = studentProfiles;
        this.studentGoals = studentGoals;
        this.messages = messages;
        this.reviews = reviews;
        this.achievements = achievements;
        this.userAchievements = userAchievements;
        this.pointEvents = pointEvents;
        this.protections = protections;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Concede los puntos de una clase y reevalúa. Idempotente por el índice único: si esta clase ya
     * dio puntos, no los vuelve a dar aunque el evento llegue dos veces.
     */
    @Transactional
    public void onLessonCompleted(UUID studentId, UUID bookingId, Instant when) {
        if (cuentaParaGamificacion(bookingId)) {
            concederSiEsNueva(studentId, FUENTE_CLASE, bookingId, PUNTOS_POR_CLASE, when);
        }
        reevaluar(studentId);
    }

    @Transactional
    public void onReviewCreated(UUID studentId, UUID reviewId, Instant when) {
        concederSiEsNueva(studentId, FUENTE_RESENA, reviewId, PUNTOS_POR_RESENA, when);
        reevaluar(studentId);
    }

    /** Reservar no da puntos: solo enciende «Primera reserva». */
    @Transactional
    public void onBookingCreated(UUID bookingId) {
        bookings.findById(bookingId).ifPresent(b -> reevaluar(b.getStudentId()));
    }

    /** Los hechos que solo mueven logros y no dan puntos directos. */
    @Transactional
    public void onSomethingHappened(UUID studentId) {
        reevaluar(studentId);
    }

    /**
     * Reevalúa desde cero: borra el estado derivado y lo reconstruye. Los puntos NO se borran —el
     * libro es append-only y su índice único es lo que hace que reconstruir no duplique.
     */
    @Transactional
    public void recompute(UUID studentId) {
        userAchievements.deleteByUserId(studentId);
        protections.deleteByUserId(studentId);
        reevaluar(studentId);
    }

    /* ------------------------------------------------------------------ */

    /**
     * Una clase gratuita no cuenta por defecto: la fija un administrador para probar el flujo en
     * producción, y las pruebas no deben contaminar el perfil de nadie. El ajuste permite cambiar
     * de opinión sin desplegar si el piloto las usa con estudiantes reales.
     */
    private boolean cuentaParaGamificacion(UUID bookingId) {
        if (settings.getBoolean(AJUSTE_GRATUITAS)) {
            return true;
        }
        return bookings.findById(bookingId)
                .flatMap(b -> professorProfiles.findById(b.getProfessorId()))
                .map(p -> p.getHourlyRateCop() == null || p.getHourlyRateCop() > 0)
                .orElse(true);
    }

    private void concederSiEsNueva(UUID userId, String fuente, UUID sourceId, int puntos, Instant when) {
        if (sourceId != null && pointEvents.existsBySourceTypeAndSourceId(fuente, sourceId)) {
            return;
        }
        pointEvents.save(new co.orion.engagement.domain.PointEvent(
                userId, fuente, sourceId, puntos, when));
    }

    /**
     * El paso central: arma la foto, evalúa los veinte y enciende lo que llegó a su meta.
     *
     * <p>Se publica <strong>un</strong> evento con todo lo desbloqueado a la vez y no uno por
     * estrella: si alguien enciende tres de golpe, tres notificaciones seguidas se leen como un
     * fallo, no como una celebración.
     */
    private void reevaluar(UUID studentId) {
        Instant ahora = clock.instant();
        AchievementInput input = fotoDe(studentId, ahora);

        // Las protecciones que el cálculo decidió gastar se persisten aquí: el cálculo es puro y
        // no escribe nada, y así recompute llega al mismo sitio que el camino incremental.
        StreakCalculator.Racha racha = StreakCalculator.calcular(
                input.clasesTomadas(), input.mesesYaProtegidos(), ahora);
        for (LocalDate semana : racha.semanasProtegidas()) {
            if (!protections.existsByUserIdAndGrantedFor(studentId, semana.withDayOfMonth(1))) {
                protections.save(new StreakProtection(studentId, semana));
            }
        }

        Map<String, UserAchievement> actuales = userAchievements.findByUserId(studentId).stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementCode, u -> u));

        List<String> encendidosAhora = new ArrayList<>();

        for (Achievement logro : achievements.findByActiveTrueOrderByDisplayOrderAsc()) {
            UserAchievement estado = actuales.computeIfAbsent(logro.getCode(),
                    code -> new UserAchievement(studentId, code));

            int progreso = AchievementEvaluators.progresoDe(
                    logro.getCriteriaType(), input, paramsDe(logro));
            estado.recordProgress(Math.min(progreso, logro.getTarget()));

            if (progreso >= logro.getTarget() && estado.unlock(ahora)) {
                concederSiEsNueva(studentId, FUENTE_LOGRO,
                        UUID.nameUUIDFromBytes((studentId + ":" + logro.getCode()).getBytes()),
                        logro.getPoints(), ahora);
                encendidosAhora.add(logro.getCode());
            }
            userAchievements.save(estado);
        }

        if (!encendidosAhora.isEmpty()) {
            events.publishEvent(new AchievementUnlockedEvent(studentId, List.copyOf(encendidosAhora)));
        }
    }

    /** Los parámetros del criterio. JSON plano y pequeño: no hace falta un parser completo. */
    private Map<String, String> paramsDe(Achievement logro) {
        String json = logro.getCriteriaParams();
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        Map<String, String> params = new java.util.HashMap<>();
        for (String par : json.replaceAll("[{}\"]", "").split(",")) {
            String[] kv = par.split(":", 2);
            if (kv.length == 2) {
                params.put(kv[0].trim(), kv[1].trim());
            }
        }
        return params;
    }

    /** La foto completa del estudiante. Una sola vez por evaluación, no una consulta por logro. */
    private AchievementInput fotoDe(UUID studentId, Instant ahora) {
        List<BookingStatus> activas = List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT);
        List<Booking> pasadas = bookings.findPastOfStudent(studentId, activas, ahora);

        boolean contarGratuitas = settings.getBoolean(AJUSTE_GRATUITAS);
        Set<UUID> profesoresGratuitos = contarGratuitas ? Set.of() : profesoresConTarifaCero(pasadas);

        List<Booking> cuentan = pasadas.stream()
                .filter(b -> LearningProgress.cuentaComoTomada(b.getStatus(), b.getEndsAt(), ahora))
                .filter(b -> !profesoresGratuitos.contains(b.getProfessorId()))
                .toList();

        List<Tomada> tomadas = cuentan.stream()
                .map(b -> new Tomada(b.getProfessorId(), b.getStartsAt(), b.getEndsAt()))
                .toList();

        Set<String> eventos = new HashSet<>();
        if (!bookings.findPastOfStudent(studentId, activas, ahora).isEmpty()
                || !bookings.findByStudentIdAndStatusInAndStartsAtAfterOrderByStartsAtAsc(
                        studentId, activas, ahora).isEmpty()) {
            eventos.add("booking_created");
        }
        if (messages.existsBySenderId(studentId)) {
            eventos.add("message_sent");
        }
        if (reviews.existsByStudentId(studentId)) {
            eventos.add("review_written");
        }
        if (!studentGoals.findByUserId(studentId).isEmpty()) {
            eventos.add("goal_declared");
        }

        return new AchievementInput(
                tomadas,
                cuentan.stream().filter(b -> b.getModality() == BookingModality.IN_PERSON).count(),
                cuentan.stream().map(Booking::getLanguageCode)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()),
                cuentan.stream().map(Booking::getProfessorId).collect(Collectors.toSet()),
                eventos,
                studentProfiles.profileCompleteness(studentId),
                diasSinCancelar(studentId, pasadas, ahora),
                protections.findByUserId(studentId).stream()
                        .map(StreakProtection::getGrantedFor).collect(Collectors.toSet()),
                ahora);
    }

    private Set<UUID> profesoresConTarifaCero(List<Booking> reservas) {
        Set<UUID> profesores = reservas.stream().map(Booking::getProfessorId).collect(Collectors.toSet());
        if (profesores.isEmpty()) {
            return Set.of();
        }
        return professorProfiles.findAllById(profesores).stream()
                .filter(p -> p.getHourlyRateCop() != null && p.getHourlyRateCop() == 0L)
                .map(p -> p.getUserId())
                .collect(Collectors.toSet());
    }

    /**
     * Días desde la última cancelación del estudiante. Sin ninguna, se cuenta desde su primera
     * reserva: alguien que lleva tres días en Orión no tiene «un mes sin cancelar».
     */
    private long diasSinCancelar(UUID studentId, List<Booking> pasadas, Instant ahora) {
        Instant ultimaCancelacion = pasadas.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED_BY_STUDENT)
                .map(Booking::getStartsAt)
                .max(Instant::compareTo)
                .orElse(null);

        Instant desde = ultimaCancelacion != null
                ? ultimaCancelacion
                : pasadas.stream().map(Booking::getStartsAt).min(Instant::compareTo).orElse(null);

        if (desde == null) {
            return 0;
        }
        LocalDate inicio = desde.atZone(BusinessZone.BOGOTA).toLocalDate();
        LocalDate hoy = ahora.atZone(BusinessZone.BOGOTA).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(inicio, hoy));
    }
}
