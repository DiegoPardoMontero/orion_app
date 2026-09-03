package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.domain.LearningProgress;
import co.orion.scheduling.domain.LearningProgress.Tomada;
import co.orion.scheduling.persistence.BookingRepository;

/**
 * El recorrido del estudiante, para su panel. Todo sale de reservas que ya existen: no hay ninguna
 * métrica inventada ni ningún dato que haya que empezar a pedirle a nadie.
 *
 * <p>La aritmética vive en {@link LearningProgress}, que es una clase pura; aquí solo se leen las
 * reservas, se traen los nombres de los profesores y se arma la respuesta.
 */
@Service
public class StudentProgressService {

    /** Un año hacia atrás: lo que dibuja el mapa de clases del panel. */
    private static final int DIAS_DEL_MAPA = 365;

    /** Cuántos profesores se listan. Más que esto deja de ser un recuerdo y pasa a ser una tabla. */
    private static final int MAX_PROFESORES = 6;

    private static final List<BookingStatus> ACTIVAS =
            List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final Clock clock;

    public StudentProgressService(BookingRepository bookings,
                                  UserRepository users,
                                  ProfessorProfileRepository profiles,
                                  Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public record ProfesorPracticado(UUID id, String fullName, String photoUrl, String headline,
                                     int lessons, Instant lastLessonAt) {
    }

    public record ProximaClase(UUID id, Instant startsAt, String modality, String meetingLink,
                               UUID professorId, String professorName, String professorPhotoUrl) {
    }

    public record Progreso(int lessonsTaken,
                           long minutesTotal,
                           int currentStreakWeeks,
                           int bestStreakWeeks,
                           ProximaClase nextLesson,
                           List<ProfesorPracticado> professors,
                           Map<LocalDate, Integer> lessonsByDay,
                           LocalDate mapFrom,
                           LocalDate today) {
    }

    @Transactional(readOnly = true)
    public Progreso of(User student) {
        Instant ahora = clock.instant();

        List<Booking> pasadas = bookings.findPastOfStudent(student.getId(), ACTIVAS, ahora);
        List<Tomada> tomadas = pasadas.stream()
                .filter(b -> LearningProgress.cuentaComoTomada(b.getStatus(), b.getEndsAt(), ahora))
                .map(b -> new Tomada(b.getProfessorId(), b.getStartsAt(), b.getEndsAt()))
                .toList();

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahora);
        LocalDate hoy = ahora.atZone(BusinessZone.BOGOTA).toLocalDate();
        LocalDate desde = hoy.minusDays(DIAS_DEL_MAPA - 1L);

        return new Progreso(
                tomadas.size(),
                LearningProgress.minutosTotales(tomadas),
                rachas.actual(),
                rachas.mejor(),
                proximaClase(student, ahora),
                profesoresDe(tomadas),
                LearningProgress.porDia(tomadas, desde),
                desde,
                hoy);
    }

    /**
     * La siguiente clase confirmada. Una reserva sin pagar queda fuera: todavía no es una clase, y
     * anunciarla en el panel con su cuenta atrás sería prometer algo que puede vencer en 20 minutos.
     */
    private ProximaClase proximaClase(User student, Instant ahora) {
        return bookings
                .findByStudentIdAndStatusInAndStartsAtAfterOrderByStartsAtAsc(
                        student.getId(), List.of(BookingStatus.CONFIRMED), ahora)
                .stream()
                .findFirst()
                .map(booking -> {
                    User profesor = users.findById(booking.getProfessorId()).orElse(null);
                    return new ProximaClase(
                            booking.getId(),
                            booking.getStartsAt(),
                            booking.getModality().name(),
                            booking.getMeetingLink(),
                            booking.getProfessorId(),
                            profesor == null ? null : profesor.getFullName(),
                            profesor == null ? null : profesor.getPhotoUrl());
                })
                .orElse(null);
    }

    /**
     * Con quién ha practicado, de quien más clases le ha dado al que menos. Se ordena por número de
     * clases y no por fecha porque la pregunta que contesta esta lista es "¿con quién aprendí?", no
     * "¿a quién vi la última vez?" — para eso está la agenda.
     */
    private List<ProfesorPracticado> profesoresDe(List<Tomada> tomadas) {
        Map<UUID, int[]> conteo = new LinkedHashMap<>();
        Map<UUID, Instant> ultima = new LinkedHashMap<>();
        for (Tomada tomada : tomadas) {
            conteo.computeIfAbsent(tomada.professorId(), id -> new int[1])[0]++;
            ultima.merge(tomada.professorId(), tomada.startsAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        if (conteo.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> personas = users.findAllById(conteo.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        Map<UUID, ProfessorProfile> fichas = profiles.findAllById(conteo.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(ProfessorProfile::getUserId, p -> p));

        return conteo.entrySet().stream()
                .map(e -> {
                    User persona = personas.get(e.getKey());
                    ProfessorProfile ficha = fichas.get(e.getKey());
                    return new ProfesorPracticado(
                            e.getKey(),
                            persona == null ? null : persona.getFullName(),
                            persona == null ? null : persona.getPhotoUrl(),
                            ficha == null ? null : ficha.getHeadline(),
                            e.getValue()[0],
                            ultima.get(e.getKey()));
                })
                .filter(p -> p.fullName() != null)
                .sorted(Comparator.comparingInt(ProfesorPracticado::lessons).reversed()
                        .thenComparing(ProfesorPracticado::lastLessonAt, Comparator.reverseOrder()))
                .limit(MAX_PROFESORES)
                .toList();
    }
}
