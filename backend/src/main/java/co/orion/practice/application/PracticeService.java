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
import java.util.stream.StreamSupport;

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
import com.fasterxml.jackson.databind.node.ObjectNode;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.practice.domain.Evaluador;
import co.orion.practice.domain.PracticeCompletedEvent;
import co.orion.practice.domain.PracticeReadyEvent;
import co.orion.practice.domain.PracticeItem;
import co.orion.practice.domain.PracticeItemType;
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
    /**
     * Lo que se le ofrece al estudiante: lo vivo y lo que se está preparando, que la invitación muestra
     * como «Estamos preparando tu práctica» (diseño del 24/09/2026). Lo que falló no se ofrece nunca.
     */
    private static final List<PracticeSetStatus> OFRECIDOS = List.of(PracticeSetStatus.PENDING,
            PracticeSetStatus.READY, PracticeSetStatus.IN_PROGRESS);
    /** Tras tres intentos sin dos ejercicios anclados, el set queda FAILED y no se ofrece. */
    static final int INTENTOS_DE_GENERACION = 3;

    private final PracticeSetRepository sets;
    private final PracticeItemRepository items;
    private final BookingRepository bookings;
    private final PracticeGenerator generador;
    private final PlatformSettingsService settings;
    private final StudentProfileRepository perfiles;
    private final RevisorDeFrases revisor;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate cadaUnoEnSuTransaccion;
    private final Clock clock;

    public PracticeService(PracticeSetRepository sets, PracticeItemRepository items, BookingRepository bookings,
                           PracticeGenerator generador, PlatformSettingsService settings, StudentProfileRepository perfiles,
                           RevisorDeFrases revisor, ApplicationEventPublisher eventos,
                           PlatformTransactionManager transacciones, Clock clock) {
        this.sets = sets;
        this.items = items;
        this.bookings = bookings;
        this.generador = generador;
        this.settings = settings;
        this.perfiles = perfiles;
        this.revisor = revisor;
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
                        g.explicacion(), g.terminoFuente(), Pistas.segura(g)));
            }
            // Un minuto por ejercicio, y nunca menos de dos: es lo que se promete en la invitación.
            set.listo(validos.size(), Math.max(2, validos.size()));
            sets.save(set);
            eventos.publishEvent(new PracticeReadyEvent(set.getId(), set.getStudentId(), set.getProfessorId(),
                    validos.size()));
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
                        estudiante.getId(), OFRECIDOS, clock.instant())
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
        boolean correcto = ejercicio.getItemType() == PracticeItemType.WRITE_SENTENCE
                ? fraseAceptada(estudiante, ejercicio, respuesta)
                : Evaluador.esCorrecta(ejercicio.getItemType(), ejercicio.getPayload(), ejercicio.getExpected(), respuesta);
        ejercicio.responder(recortar(respuesta), correcto, max, clock.instant());
        items.save(ejercicio);
        sets.save(set);
        return new Resultado(ejercicio, correcto, ejercicio.cerrado(max), Math.max(0, max - ejercicio.getAttempts()));
    }

    /** Saltar un ejercicio de escucha: el dispositivo no tiene voz en inglés. No cuenta como error. */
    @Transactional
    public PracticeItem saltar(User estudiante, UUID ejercicioId) {
        PracticeItem ejercicio = items.findById(ejercicioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejercicio no encontrado"));
        PracticeSet set = suyo(estudiante, ejercicio.getPracticeSetId());
        set.exigirVivo(clock.instant());
        set.empezar(clock.instant());
        ejercicio.saltar(settings.getInt("practice_max_attempts"), clock.instant());
        sets.save(set);
        return items.save(ejercicio);
    }

    public record ResultadoDePareja(PracticeItem ejercicio, boolean va, boolean cerrado, int intentosQueQuedan) {
    }

    /**
     * Unir un par en Parejas. Un par que va se queda unido; uno que no va gasta un intento. Unir de
     * nuevo algo ya unido es 422: la pantalla no lo ofrece.
     */
    @Transactional
    public ResultadoDePareja unirPareja(User estudiante, UUID ejercicioId, String termino, String significado) {
        PracticeItem ejercicio = items.findById(ejercicioId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejercicio no encontrado"));
        PracticeSet set = suyo(estudiante, ejercicio.getPracticeSetId());
        set.exigirVivo(clock.instant());
        set.empezar(clock.instant());
        int max = settings.getInt("practice_max_attempts");
        Map<String, String> esperados = new LinkedHashMap<>();
        ObjectNode unidos;
        try {
            JSON.readTree(ejercicio.getExpected()).fields()
                    .forEachRemaining(e -> esperados.put(Evaluador.normalizar(e.getKey()), e.getValue().asText()));
            unidos = ejercicio.getAnswer() != null && ejercicio.getAnswer().startsWith("{")
                    ? (ObjectNode) JSON.readTree(ejercicio.getAnswer()) : JSON.createObjectNode();
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new UnprocessableException("Este ejercicio no se puede unir de a una.");
        }
        boolean yaUnido = unidos.has(termino) || StreamSupport.stream(unidos.spliterator(), false)
                .anyMatch(v -> Evaluador.normalizar(v.asText()).equals(Evaluador.normalizar(significado)));
        if (yaUnido) {
            throw new UnprocessableException("Esa ya está unida. Elige otra.");
        }
        String correcto = esperados.get(Evaluador.normalizar(termino));
        boolean va = correcto != null && Evaluador.normalizar(correcto).equals(Evaluador.normalizar(significado));
        if (va) {
            unidos.put(termino, significado);
        }
        String fallo = JSON.createObjectNode().put(termino, significado).toString();
        ejercicio.pareja(va, unidos.size() == esperados.size(), unidos.toString(), fallo, max, clock.instant());
        items.save(ejercicio);
        sets.save(set);
        return new ResultadoDePareja(ejercicio, va, ejercicio.cerrado(max), Math.max(0, max - ejercicio.getAttempts()));
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
                    correctos, set.getCompletedAt(), PracticeItem.perfecta(suyos),
                    (int) suyos.stream().filter(i -> i.getItemType().seOye() && Boolean.TRUE.equals(i.getCorrect())).count(),
                    (int) suyos.stream().filter(PracticeItem::alSegundoIntento).count()));
        }
        return new ConEjercicios(set, suyos);
    }

    /**
     * «Tu frase»: la revisa la IA —inglés con sentido que usa el término o una flexión—, y si no hay
     * quién revise, la regla de siempre (el término, en una frase de cuatro palabras o más).
     */
    private boolean fraseAceptada(User estudiante, PracticeItem ejercicio, String respuesta) {
        boolean porRegla = Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, ejercicio.getPayload(), null, respuesta);
        if (respuesta == null || respuesta.isBlank()) {
            return false;
        }
        String termino;
        try {
            termino = JSON.readTree(ejercicio.getPayload()).path("term").asText("");
        } catch (JsonProcessingException ex) {
            return porRegla;
        }
        return revisor.acepta(estudiante.getId(), termino, recortar(respuesta)).orElse(porRegla);
    }

    /** Cuántos intentos tiene cada ejercicio (ajuste {@code practice_max_attempts}). */
    public int maxIntentos() {
        return settings.getInt("practice_max_attempts");
    }

    /** Las terminadas y las vencidas, con sus ejercicios: «Mi cielo» dice cuáles fueron perfectas. */
    @Transactional(readOnly = true)
    public List<ConEjercicios> historial(User estudiante) {
        List<PracticeSet> suyos = sets.findByStudentIdAndStatusInOrderByCreatedAtDesc(estudiante.getId(),
                List.of(PracticeSetStatus.COMPLETED, PracticeSetStatus.EXPIRED));
        Map<UUID, List<PracticeItem>> porSet = suyos.isEmpty() ? Map.of()
                : items.findByPracticeSetIdIn(suyos.stream().map(PracticeSet::getId).toList()).stream()
                        .collect(Collectors.groupingBy(PracticeItem::getPracticeSetId));
        return suyos.stream().map(s -> new ConEjercicios(s, porSet.getOrDefault(s.getId(), List.of()))).toList();
    }

    /* ---------------- lo que ve el profesor ---------------- */

    /**
     * El resumen de la semana: cuántas practicó y dónde le costó. Desde el 24/09/2026 el profesor ve
     * también cada ejercicio con lo que respondió su estudiante (decisión de Pardo, que cambió el
     * «nunca las respuestas» del brief, paso B5.4): el estudiante lo sabe desde la portada de su
     * práctica, porque equivocarse sin saber que alguien mira sería lo contrario de confiar.
     *
     * @param leCosto los términos (o tipos de ejercicio) donde más falló en las últimas cuatro semanas
     */
    public record Resumen(int ofrecidasEstaSemana, int completadasEstaSemana, int ofrecidasEsteMes,
                          int completadasEsteMes, List<String> leCosto) {
    }

    @Transactional(readOnly = true)
    public Resumen paraElProfesor(User profesor, UUID estudianteId) {
        if (!bookings.existsByProfessorIdAndStudentId(profesor.getId(), estudianteId)) {
            throw new ResourceNotFoundException("Estudiante no encontrado");
        }
        Instant ahora = clock.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, BusinessZone.BOGOTA);
        Instant lunes = hoy.with(DayOfWeek.MONDAY).atStartOfDay(BusinessZone.BOGOTA).toInstant();
        // La ficha dice «este mes» (diseño del 24/09/2026): se mira desde el día 1, o cuatro semanas
        // atrás si el mes acaba de empezar y lo que le costó está en el anterior.
        Instant primeroDelMes = hoy.withDayOfMonth(1).atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Instant cuatroSemanas = ahora.minus(Duration.ofDays(28));
        List<PracticeSet> recientes = sets.findByStudentIdAndProfessorIdAndCreatedAtGreaterThanEqual(
                estudianteId, profesor.getId(), primeroDelMes.isBefore(cuatroSemanas) ? primeroDelMes : cuatroSemanas);

        List<PracticeSet> estaSemana = recientes.stream()
                .filter(s -> !s.getCreatedAt().isBefore(lunes))
                .filter(s -> s.getStatus() != PracticeSetStatus.PENDING && s.getStatus() != PracticeSetStatus.FAILED)
                .toList();
        int completadas = (int) estaSemana.stream().filter(s -> s.getStatus() == PracticeSetStatus.COMPLETED).count();
        List<PracticeSet> esteMes = recientes.stream()
                .filter(s -> !s.getCreatedAt().isBefore(primeroDelMes))
                .filter(s -> s.getStatus() != PracticeSetStatus.PENDING && s.getStatus() != PracticeSetStatus.FAILED)
                .toList();
        int completadasDelMes = (int) esteMes.stream().filter(s -> s.getStatus() == PracticeSetStatus.COMPLETED).count();

        Map<String, Long> fallos = new LinkedHashMap<>();
        if (!recientes.isEmpty()) {
            items.findByPracticeSetIdIn(recientes.stream().map(PracticeSet::getId).toList()).stream()
                    .filter(i -> i.costo(maxIntentos()))
                    .map(i -> i.getSourceTerm() != null ? i.getSourceTerm() : nombreDelTipo(i))
                    .forEach(t -> fallos.merge(t, 1L, Long::sum));
        }
        List<String> leCosto = fallos.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3).map(Map.Entry::getKey).collect(Collectors.toCollection(ArrayList::new));
        return new Resumen(estaSemana.size(), completadas, esteMes.size(), completadasDelMes, leCosto);
    }

    /**
     * Los ejercicios que salieron de un acta, para el profesor que la escribió: cada uno con su
     * respuesta esperada y lo que hizo el estudiante (sus respuestas, sus intentos, si acertó o lo
     * saltó). Vacío si el acta aún no tiene práctica.
     */
    @Transactional(readOnly = true)
    public Optional<ConEjercicios> delActa(User profesor, UUID actaId) {
        Optional<PracticeSet> set = sets.findByLessonNoteId(actaId);
        if (set.isPresent() && !set.get().getProfessorId().equals(profesor.getId())) {
            throw new ResourceNotFoundException("Práctica no encontrada");
        }
        return set.map(s -> new ConEjercicios(s, items.findByPracticeSetIdOrderByItemIndexAsc(s.getId())));
    }

    /** Las prácticas de un estudiante con este profesor, para su ficha: de la más nueva a la más vieja. */
    @Transactional(readOnly = true)
    public List<ConEjercicios> historialParaElProfesor(User profesor, UUID estudianteId) {
        if (!bookings.existsByProfessorIdAndStudentId(profesor.getId(), estudianteId)) {
            throw new ResourceNotFoundException("Estudiante no encontrado");
        }
        List<PracticeSet> suyos = sets.findTop20ByStudentIdAndProfessorIdAndStatusNotInOrderByCreatedAtDesc(estudianteId,
                profesor.getId(), List.of(PracticeSetStatus.PENDING, PracticeSetStatus.FAILED));
        // Con los ejercicios, de una sola consulta: la fila de cada set pinta su constelación.
        Map<UUID, List<PracticeItem>> porSet = suyos.isEmpty() ? Map.of()
                : items.findByPracticeSetIdIn(suyos.stream().map(PracticeSet::getId).toList()).stream()
                        .collect(Collectors.groupingBy(PracticeItem::getPracticeSetId));
        return suyos.stream().map(s -> new ConEjercicios(s, porSet.getOrDefault(s.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(PracticeItem::getItemIndex)).toList())).toList();
    }

    private static String nombreDelTipo(PracticeItem i) {
        return switch (i.getItemType()) {
            case FIX_SENTENCE -> "corregir frases";
            case ORDER_DIALOGUE -> "ordenar diálogos";
            case MATCH_MEANING -> "significados";
            case FILL_BLANK -> "completar frases";
            case WRITE_SENTENCE -> "escribir frases propias";
            case SPOT_ERROR -> "encontrar errores";
            case BUILD_SENTENCE -> "armar frases";
            case CHOOSE_REPLY -> "responder en una conversación";
            case LISTEN_CHOOSE -> "reconocer palabras de oído";
            case DICTATION -> "escribir lo que oye";
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
