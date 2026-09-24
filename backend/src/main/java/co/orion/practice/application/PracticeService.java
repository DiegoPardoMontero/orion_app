package co.orion.practice.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.practice.domain.Evaluador;
import co.orion.practice.domain.PracticeCompletedEvent;
import co.orion.practice.domain.PracticeItem;
import co.orion.practice.domain.PracticeSet;
import co.orion.practice.domain.PracticeSetStatus;
import co.orion.practice.persistence.PracticeItemRepository;
import co.orion.practice.persistence.PracticeSetRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.text.TextoParaIa;
import co.orion.shared.time.BusinessZone;

/**
 * La práctica entre clases (brief del Bloque 10, Parte B).
 *
 * <p><strong>Nunca se genera al abrir la pantalla.</strong> Publicar un acta crea el set en
 * {@code PENDING}; un trabajo lo genera y lo deja {@code READY}. El estudiante no espera, y no se
 * paga una llamada por cada visita.
 *
 * <p>Solo el dueño opera sobre su set: a cualquier otro, 404, porque no se confirma que exista.
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<PracticeSetStatus> VIVOS = List.of(PracticeSetStatus.READY, PracticeSetStatus.IN_PROGRESS);
    /** Tras tres intentos sin dos ejercicios anclados, el set queda FAILED y no se ofrece. */
    static final int INTENTOS_DE_GENERACION = 3;

    private final PracticeSetRepository sets;
    private final PracticeItemRepository items;
    private final BookingRepository bookings;
    private final PracticeGenerator generador;
    private final PlatformSettingsService settings;
    private final StudentProfileRepository perfiles;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate cadaUnoEnSuTransaccion;
    private final Clock clock;

    public PracticeService(PracticeSetRepository sets, PracticeItemRepository items, BookingRepository bookings,
                           PracticeGenerator generador, PlatformSettingsService settings, StudentProfileRepository perfiles,
                           ApplicationEventPublisher eventos, PlatformTransactionManager transacciones, Clock clock) {
        this.sets = sets;
        this.items = items;
        this.bookings = bookings;
        this.generador = generador;
        this.settings = settings;
        this.perfiles = perfiles;
        this.eventos = eventos;
        this.cadaUnoEnSuTransaccion = new TransactionTemplate(transacciones);
        this.cadaUnoEnSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /* ---------------- el set nace del acta ---------------- */

    /**
     * Un acta publicada encola su set. Republicarla corregida no crea otro: un acta, un set, para
     * siempre (y la base lo garantiza con {@code lesson_note_id UNIQUE}).
     */
    @Transactional
    public Optional<PracticeSet> crearDesdeActa(UUID actaId, UUID estudianteId, UUID profesorId, Material material) {
        if (!settings.getBoolean("practice_enabled") || sets.existsByLessonNoteId(actaId)) {
            return Optional.empty();
        }
        Instant vence = clock.instant().plus(Duration.ofDays(settings.getInt("practice_set_ttl_days")));
        // D7: el nivel y el objetivo del estudiante viajan con el material, como estaban al publicarse.
        Material conEstudiante = perfiles.findById(estudianteId)
                .map(p -> material.conEstudiante(
                        p.getSelfDeclaredLevel() == null ? null : p.getSelfDeclaredLevel().name(),
                        TextoParaIa.unaLinea(p.getMotivation(), 280)))
                .orElse(material);
        try {
            return Optional.of(sets.saveAndFlush(new PracticeSet(estudianteId, actaId, profesorId,
                    conEstudiante.languageCode(), JSON.writeValueAsString(conEstudiante), vence)));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** El trabajo diferido: genera los sets pendientes, cada uno en su propia transacción. */
    public int generarPendientes() {
        if (!generador.disponible()) {
            return 0;
        }
        int listos = 0;
        for (PracticeSet pendiente : sets.findByStatusOrderByCreatedAtAsc(PracticeSetStatus.PENDING, Limit.of(10))) {
            try {
                Boolean listo = cadaUnoEnSuTransaccion.execute(estado -> generar(pendiente.getId()));
                if (Boolean.TRUE.equals(listo)) {
                    listos++;
                }
            } catch (PracticeGenerator.ProveedorNoRespondio ex) {
                // El set sigue pendiente y sin gastar intento. Los demás de esta corrida esperarían lo
                // mismo —hasta un minuto cada uno—, así que la corrida para aquí.
                log.info("El proveedor no respondió; {} y los que siguen esperan a la próxima corrida",
                        pendiente.getId());
                break;
            } catch (RuntimeException ex) {
                // Un set que falla no frena a los demás: queda pendiente para la siguiente corrida.
                log.warn("No se pudo generar el set {}: {}", pendiente.getId(), ex.getMessage());
            }
        }
        return listos;
    }

    private boolean generar(UUID setId) {
        PracticeSet set = sets.reclamarPendiente(setId).orElse(null);
        if (set == null) {
            return false;
        }
        Material material;
        try {
            material = JSON.readValue(set.getMaterial(), Material.class);
        } catch (JsonProcessingException ex) {
            set.fallido();
            sets.save(set);
            return false;
        }
        int cuantos = settings.getInt("practice_items_per_set");
        set.intentoDeGeneracion();
        List<PracticeGenerator.Generado> validos = ValidadorDeEjercicios.validos(
                generador.generar(set.getStudentId(), material, cuantos), material, cuantos);
        if (validos.size() >= 2) {
            for (int i = 0; i < validos.size(); i++) {
                PracticeGenerator.Generado g = validos.get(i);
                items.save(new PracticeItem(set.getId(), i, g.tipo(), g.prompt(), g.payload(), g.expected(),
                        g.explicacion(), g.terminoFuente()));
            }
            // Un minuto por ejercicio, y nunca menos de dos: es lo que se promete en la invitación.
            set.listo(validos.size(), Math.max(2, validos.size()));
            sets.save(set);
            return true;
        }
        if (set.getGenerationAttempts() >= INTENTOS_DE_GENERACION) {
            log.info("El set {} quedó sin práctica: {} ejercicio(s) anclados al acta", set.getId(), validos.size());
            set.fallido();
        }
        sets.save(set);
        return false;
    }

    /** Los vencidos dejan de ofrecerse. */
    @Transactional
    public int expirarVencidos() {
        Instant ahora = clock.instant();
        List<PracticeSet> vencidos = sets.findByStatusInAndExpiresAtLessThanEqual(VIVOS, ahora);
        vencidos.forEach(s -> s.expirar(ahora));
        sets.saveAll(vencidos);
        return vencidos.size();
    }

    /* ---------------- lo que hace el estudiante ---------------- */

    public record ConEjercicios(PracticeSet set, List<PracticeItem> ejercicios) {
    }

    /** El set que se le ofrece ahora, si hay uno vivo. */
    @Transactional(readOnly = true)
    public Optional<ConEjercicios> activo(User estudiante) {
        if (!settings.getBoolean("practice_enabled")) {
            return Optional.empty();
        }
        return sets.findFirstByStudentIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        estudiante.getId(), VIVOS, clock.instant())
                .map(s -> new ConEjercicios(s, items.findByPracticeSetIdOrderByItemIndexAsc(s.getId())));
    }

    @Transactional(readOnly = true)
    public ConEjercicios uno(User estudiante, UUID setId) {
        PracticeSet set = suyo(estudiante, setId);
        return new ConEjercicios(set, items.findByPracticeSetIdOrderByItemIndexAsc(set.getId()));
    }

    @Transactional
    public ConEjercicios empezar(User estudiante, UUID setId) {
        PracticeSet set = suyo(estudiante, setId);
        set.empezar(clock.instant());
        sets.save(set);
        return new ConEjercicios(set, items.findByPracticeSetIdOrderByItemIndexAsc(set.getId()));
    }

    public record Resultado(PracticeItem ejercicio, boolean correcto, boolean cerrado, int intentosQueQuedan) {
    }

    /** Responder un ejercicio. Pasados los intentos, 422: ya se mostró la respuesta y se sigue. */
    @Transactional
    public Resultado responder(User estudiante, UUID ejercicioId, String respuesta) {
        PracticeItem ejercicio = items.findById(ejercicioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejercicio no encontrado"));
        PracticeSet set = suyo(estudiante, ejercicio.getPracticeSetId());
        set.exigirVivo(clock.instant());
        set.empezar(clock.instant());
        int max = settings.getInt("practice_max_attempts");
        boolean correcto = Evaluador.esCorrecta(ejercicio.getItemType(), ejercicio.getPayload(),
                ejercicio.getExpected(), respuesta);
        ejercicio.responder(recortar(respuesta), correcto, max, clock.instant());
        items.save(ejercicio);
        sets.save(set);
        return new Resultado(ejercicio, correcto, ejercicio.cerrado(max), Math.max(0, max - ejercicio.getAttempts()));
    }

    /**
     * Cerrar el set. Idempotente: la segunda vez devuelve el mismo resumen, sin recalcular ni
     * volver a publicar el evento que da los puntos. Y solo con todos los ejercicios cerrados —como
     * en la pantalla, que no ofrece «Ver cómo me fue» antes—: los puntos y la semana de racha son
     * por practicar, no por llamar al endpoint.
     */
    @Transactional
    public ConEjercicios completar(User estudiante, UUID setId) {
        PracticeSet set = suyo(estudiante, setId);
        List<PracticeItem> suyos = items.findByPracticeSetIdOrderByItemIndexAsc(set.getId());
        int max = maxIntentos();
        long abiertos = suyos.stream().filter(i -> !i.cerrado(max)).count();
        if (set.getStatus() != PracticeSetStatus.COMPLETED && abiertos > 0) {
            throw new UnprocessableException(abiertos == 1
                    ? "Te falta un ejercicio. Respóndelo y ves cómo te fue."
                    : "Te faltan " + abiertos + " ejercicios. Respóndelos y ves cómo te fue.");
        }
        int correctos = (int) suyos.stream().filter(i -> Boolean.TRUE.equals(i.getCorrect())).count();
        if (set.completar(correctos, clock.instant())) {
            sets.save(set);
            eventos.publishEvent(new PracticeCompletedEvent(set.getStudentId(), set.getId(), suyos.size(),
                    correctos, set.getCompletedAt()));
        }
        return new ConEjercicios(set, suyos);
    }

    /** Cuántos intentos tiene cada ejercicio (ajuste {@code practice_max_attempts}). */
    public int maxIntentos() {
        return settings.getInt("practice_max_attempts");
    }

    @Transactional(readOnly = true)
    public List<PracticeSet> historial(User estudiante) {
        return sets.findByStudentIdAndStatusInOrderByCreatedAtDesc(estudiante.getId(),
                List.of(PracticeSetStatus.COMPLETED, PracticeSetStatus.EXPIRED));
    }

    /* ---------------- lo que ve el profesor ---------------- */

    /**
     * Un resumen agregado, nunca las respuestas una por una: si el estudiante siente que sus
     * ejercicios son vigilados, deja de arriesgarse a equivocarse, y equivocarse en privado es
     * justamente el valor de la función (brief, paso B5.4).
     *
     * @param leCosto los términos (o tipos de ejercicio) donde más falló en las últimas cuatro semanas
     */
    public record Resumen(int ofrecidasEstaSemana, int completadasEstaSemana, List<String> leCosto) {
    }

    @Transactional(readOnly = true)
    public Resumen paraElProfesor(User profesor, UUID estudianteId) {
        if (!bookings.existsByProfessorIdAndStudentId(profesor.getId(), estudianteId)) {
            throw new ResourceNotFoundException("Estudiante no encontrado");
        }
        Instant ahora = clock.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, BusinessZone.BOGOTA);
        Instant lunes = hoy.with(DayOfWeek.MONDAY).atStartOfDay(BusinessZone.BOGOTA).toInstant();
        List<PracticeSet> recientes = sets.findByStudentIdAndProfessorIdAndCreatedAtGreaterThanEqual(
                estudianteId, profesor.getId(), ahora.minus(Duration.ofDays(28)));

        List<PracticeSet> estaSemana = recientes.stream()
                .filter(s -> !s.getCreatedAt().isBefore(lunes))
                .filter(s -> s.getStatus() != PracticeSetStatus.PENDING && s.getStatus() != PracticeSetStatus.FAILED)
                .toList();
        int completadas = (int) estaSemana.stream().filter(s -> s.getStatus() == PracticeSetStatus.COMPLETED).count();

        Map<String, Long> fallos = new LinkedHashMap<>();
        if (!recientes.isEmpty()) {
            items.findByPracticeSetIdIn(recientes.stream().map(PracticeSet::getId).toList()).stream()
                    .filter(i -> Boolean.FALSE.equals(i.getCorrect()))
                    .map(i -> i.getSourceTerm() != null ? i.getSourceTerm() : nombreDelTipo(i))
                    .forEach(t -> fallos.merge(t, 1L, Long::sum));
        }
        List<String> leCosto = fallos.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3).map(Map.Entry::getKey).collect(Collectors.toCollection(ArrayList::new));
        return new Resumen(estaSemana.size(), completadas, leCosto);
    }

    private static String nombreDelTipo(PracticeItem i) {
        return switch (i.getItemType()) {
            case FIX_SENTENCE -> "corregir frases";
            case ORDER_DIALOGUE -> "ordenar diálogos";
            case MATCH_MEANING -> "significados";
            case FILL_BLANK -> "completar frases";
            case WRITE_SENTENCE -> "escribir frases propias";
        };
    }

    private PracticeSet suyo(User estudiante, UUID setId) {
        return sets.findById(setId)
                .filter(s -> s.getStudentId().equals(estudiante.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Práctica no encontrada"));
    }

    private static String recortar(String respuesta) {
        if (respuesta == null) {
            return null;
        }
        return respuesta.length() > 600 ? respuesta.substring(0, 600) : respuesta;
    }
}
