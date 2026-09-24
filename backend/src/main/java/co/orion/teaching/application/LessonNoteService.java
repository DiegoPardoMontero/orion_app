package co.orion.teaching.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.StudentProfile;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.text.TextoParaIa;
import co.orion.teaching.application.LessonNoteDrafter.Borrador;
import co.orion.teaching.application.LessonNoteDrafter.Palabra;
import co.orion.teaching.domain.LessonNote;
import co.orion.teaching.domain.LessonNotePublishedEvent;
import co.orion.teaching.domain.LessonNoteStatus;
import co.orion.teaching.domain.LessonVocabulary;
import co.orion.teaching.persistence.LessonNoteRepository;
import co.orion.teaching.persistence.LessonVocabularyRepository;

/**
 * El acta de clase (Bloque 10, Parte A): del crudo del profesor al acta que lee el estudiante.
 *
 * <p>Las cinco reglas del brief se sostienen aquí: la IA reorganiza y no inventa (lo valida el
 * generador), nada llega al estudiante sin que el profesor publique, la función nunca bloquea (sin
 * IA, el profesor escribe a mano en los mismos campos), la entrada es una caja de texto libre, y el
 * estudiante nunca ve lo que el profesor escribió en crudo ni cuánto corrigió el borrador.
 *
 * <p><strong>Quién puede qué.</strong> Crear, editar y publicar: solo el profesor de esa reserva;
 * otro profesor recibe 403. Leer: el profesor la suya; el estudiante solo si está publicada — un
 * borrador es 404 para él, no 403, porque no se confirma que exista algo que no debe ver. Un
 * tercero sin relación, 404.
 */
@Service
public class LessonNoteService {

    private final LessonNoteRepository notas;
    private final LessonVocabularyRepository vocabulario;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final StudentProfileRepository perfiles;
    private final LessonNoteDrafter redactor;
    private final LessonNotesLaunch lanzamiento;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public LessonNoteService(LessonNoteRepository notas, LessonVocabularyRepository vocabulario,
                             BookingRepository bookings, UserRepository users,
                             StudentProfileRepository perfiles, LessonNoteDrafter redactor,
                             LessonNotesLaunch lanzamiento, PlatformSettingsService settings,
                             ApplicationEventPublisher eventos, Clock clock) {
        this.notas = notas;
        this.vocabulario = vocabulario;
        this.bookings = bookings;
        this.users = users;
        this.perfiles = perfiles;
        this.redactor = redactor;
        this.lanzamiento = lanzamiento;
        this.settings = settings;
        this.eventos = eventos;
        this.clock = clock;
    }

    /** Un acta con su vocabulario, y si la propuso la IA en este llamado. */
    /** Cuándo empezó la clase del acta: el encabezado la nombra («Clase del miércoles 23 sep»). */
    @Transactional(readOnly = true)
    public Instant empiezaLaClase(UUID bookingId) {
        return bookings.findById(bookingId).map(Booking::getStartsAt).orElse(null);
    }

    public record Acta(LessonNote nota, List<LessonVocabulary> palabras, boolean propuestaPorIa) {
    }

    /**
     * Genera el borrador desde las notas del profesor. Si la IA no responde, el borrador queda en
     * blanco con las notas guardadas: «No pudimos armar el borrador. Puedes escribirla tú; ya
     * guardamos tus notas.»
     */
    @Transactional
    public Acta redactar(User profesor, UUID bookingId, String crudo) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Clase no encontrada"));
        exigirProfesorDe(b, profesor);
        if (b.getStatus() != BookingStatus.COMPLETED) {
            throw new UnprocessableException(
                    "El acta se escribe cuando la clase ya se dictó y quedó cerrada.");
        }
        if (b.getCompletedAt() == null || b.getCompletedAt().isBefore(lanzamiento.desde())) {
            throw new UnprocessableException(
                    "Las actas son para las clases que se cierran desde que llegó esta función.");
        }

        Instant ahora = clock.instant();
        LessonNote nota = notas.findByBookingId(b.getId()).orElseGet(() -> notas.save(
                new LessonNote(b.getId(), b.getProfessorId(), b.getStudentId(), b.getLanguageCode(),
                        crudo, ahora)));

        // Apagada desde Ajustes, la función no desaparece: el profesor escribe el acta a mano en
        // los mismos cuatro campos. El interruptor vale para cualquier redactor, también el local.
        Optional<Borrador> borrador = settings.getBoolean("ai_lesson_notes_enabled")
                ? redactor.redactar(contexto(b, profesor, crudo))
                : Optional.empty();
        List<LessonVocabulary> palabras;
        if (borrador.isPresent()) {
            Borrador p = borrador.get();
            nota.aMano(crudo, ahora);
            nota.proponer(p.workedOn(), p.recurringIssues(), p.nextSteps(),
                    texto(p.workedOn(), p.recurringIssues(), p.nextSteps(), p.vocabulario()),
                    p.promptVersion(), ahora);
            palabras = reemplazarVocabulario(notas.save(nota), p.vocabulario());
        } else {
            nota.aMano(crudo, ahora);
            palabras = vocabulario.findByLessonNoteIdOrderByDisplayOrderAsc(notas.save(nota).getId());
        }
        return new Acta(nota, palabras, borrador.isPresent());
    }

    /** Guardar cambios, en borrador o dentro de la ventana de edición tras publicar (D2). */
    @Transactional
    public Acta guardar(User profesor, UUID notaId, String trabajado, String presente, String sigue,
                        List<Palabra> palabras) {
        LessonNote nota = suyaComoProfesor(profesor, notaId);
        boolean publicada = nota.getStatus() == LessonNoteStatus.PUBLISHED;
        nota.editar(limpio(trabajado), limpio(presente), limpio(sigue), ventana(), clock.instant());
        List<LessonVocabulary> guardadas = reemplazarVocabulario(notas.save(nota), palabras);
        if (publicada) {
            eventos.publishEvent(evento(nota, guardadas, true));
        }
        return new Acta(nota, guardadas, false);
    }

    /** Publicar. Idempotente: la segunda vez no cambia nada ni vuelve a avisar. */
    @Transactional
    public Acta publicar(User profesor, UUID notaId) {
        LessonNote nota = suyaComoProfesor(profesor, notaId);
        List<LessonVocabulary> palabras = vocabulario.findByLessonNoteIdOrderByDisplayOrderAsc(nota.getId());
        String publicado = texto(nota.getWorkedOn(), nota.getRecurringIssues(), nota.getNextSteps(),
                palabras.stream().map(v -> new Palabra(v.getTerm(), v.getMeaning())).toList());
        if (nota.publicar(publicado, clock.instant())) {
            notas.save(nota);
            eventos.publishEvent(evento(nota, palabras, false));
        }
        return new Acta(nota, palabras, false);
    }

    /** Lo que ve quien pide el acta de una clase: el profesor, la suya; el estudiante, solo publicada. */
    @Transactional(readOnly = true)
    public Optional<Acta> deLaClase(User quien, UUID bookingId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Acta no encontrada"));
        boolean esProfesor = b.getProfessorId().equals(quien.getId());
        boolean esEstudiante = b.getStudentId().equals(quien.getId());
        if (!esProfesor && !esEstudiante) {
            throw new ResourceNotFoundException("Acta no encontrada");
        }
        return notas.findByBookingId(bookingId)
                .filter(n -> esProfesor || n.getStatus() == LessonNoteStatus.PUBLISHED)
                .map(n -> new Acta(n, vocabulario.findByLessonNoteIdOrderByDisplayOrderAsc(n.getId()), false));
    }

    @Transactional(readOnly = true)
    public List<Acta> delProfesor(User profesor, boolean soloBorradores) {
        List<LessonNote> suyas = soloBorradores
                ? notas.findByProfessorIdAndStatusOrderByCreatedAtDesc(profesor.getId(), LessonNoteStatus.DRAFT)
                : notas.findByProfessorIdOrderByCreatedAtDesc(profesor.getId());
        return conVocabulario(suyas);
    }

    @Transactional(readOnly = true)
    public List<Acta> delEstudiante(User estudiante, int pagina) {
        return conVocabulario(notas.findByStudentIdAndStatusOrderByPublishedAtDesc(
                estudiante.getId(), LessonNoteStatus.PUBLISHED, PageRequest.of(Math.max(0, pagina), 20)));
    }

    /**
     * Qué clases de esta persona tienen acta y en qué estado, en una sola consulta: la lista de
     * «Mis clases» decide con esto qué botón pinta en cada tarjeta. Al profesor se le cuentan
     * además como {@code PENDING} las clases cerradas desde que existen las actas y sin acta
     * todavía —el mismo criterio que {@link #redactar} aplica—, para que la pantalla no tenga que
     * adivinarlo. Al estudiante solo le cuentan las publicadas; un borrador no existe para él.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> estados(User quien, boolean comoProfesor) {
        Map<UUID, String> porClase = new HashMap<>();
        if (comoProfesor) {
            bookings.findByProfessorIdAndStatusAndCompletedAtGreaterThanEqual(
                            quien.getId(), BookingStatus.COMPLETED, lanzamiento.desde())
                    .forEach(b -> porClase.put(b.getId(), "PENDING"));
            notas.findByProfessorIdOrderByCreatedAtDesc(quien.getId())
                    .forEach(n -> porClase.put(n.getBookingId(), n.getStatus().name()));
        } else {
            notas.findByStudentIdAndStatus(quien.getId(), LessonNoteStatus.PUBLISHED)
                    .forEach(n -> porClase.put(n.getBookingId(), n.getStatus().name()));
        }
        return porClase;
    }

    /** Una línea de la lista de actas: la clase, con quién y en qué va su acta. */
    public record Entrada(UUID bookingId, Instant claseEmpieza, UUID contraparteId, String estado,
                          Instant publicada) {
    }

    /**
     * La lista de actas de quien pregunta (brief, A5.4). Al profesor, lo que le falta: las clases
     * cerradas sin acta ({@code PENDING}) y los borradores, de la más reciente a la más vieja. Al
     * estudiante, lo publicado. Las clases se leen de una vez, no una por fila.
     */
    @Transactional(readOnly = true)
    public List<Entrada> indice(User quien, boolean comoProfesor) {
        Map<UUID, String> estados = estados(quien, comoProfesor);
        Map<UUID, Instant> publicadas = new HashMap<>();
        if (!comoProfesor) {
            notas.findByStudentIdAndStatus(quien.getId(), LessonNoteStatus.PUBLISHED)
                    .forEach(n -> publicadas.put(n.getBookingId(), n.getPublishedAt()));
        }
        List<UUID> ids = estados.entrySet().stream()
                .filter(e -> !comoProfesor || !"PUBLISHED".equals(e.getValue()))
                .map(Map.Entry::getKey).toList();
        return bookings.findAllById(ids).stream()
                .map(b -> new Entrada(b.getId(), b.getStartsAt(),
                        comoProfesor ? b.getStudentId() : b.getProfessorId(),
                        estados.get(b.getId()), publicadas.get(b.getId())))
                .sorted(Comparator.comparing(Entrada::claseEmpieza).reversed())
                .toList();
    }

    public Instant desde() {
        return lanzamiento.desde();
    }

    public Duration ventana() {
        return Duration.ofHours(settings.getInt("lesson_note_edit_window_hours"));
    }

    /* ---------------- interno ---------------- */

    /** El evento lleva lo que el acta dice: la práctica lo usa sin tener que leer el acta. */
    private LessonNotePublishedEvent evento(LessonNote nota, List<LessonVocabulary> palabras, boolean actualizacion) {
        Instant clase = bookings.findById(nota.getBookingId()).map(Booking::getStartsAt).orElse(null);
        return new LessonNotePublishedEvent(nota.getId(), nota.getBookingId(), nota.getStudentId(),
                nota.getProfessorId(), actualizacion, nota.getLanguageCode(), clase, nota.getWorkedOn(),
                nota.getRecurringIssues(), nota.getNextSteps(),
                palabras.stream().map(v -> new LessonNotePublishedEvent.Termino(v.getTerm(), v.getMeaning())).toList());
    }

    private void exigirProfesorDe(Booking b, User profesor) {
        if (!b.getProfessorId().equals(profesor.getId())) {
            throw new ForbiddenException("Solo el profesor de esta clase puede escribir su acta.");
        }
    }

    private LessonNote suyaComoProfesor(User profesor, UUID notaId) {
        LessonNote nota = notas.findById(notaId)
                .orElseThrow(() -> new ResourceNotFoundException("Acta no encontrada"));
        if (!nota.getProfessorId().equals(profesor.getId())) {
            throw new ForbiddenException("Solo el profesor de esta clase puede editar su acta.");
        }
        return nota;
    }

    /**
     * D7: al proveedor solo va el nombre de pila, el idioma, el nivel y el objetivo. El objetivo es
     * la motivación que el estudiante escribió en su ficha (Pardo lo aprobó el 23/09/2026), en una
     * línea y con tope.
     */
    LessonNoteDrafter.Contexto contexto(Booking b, User profesor, String crudo) {
        String nombre = users.findById(b.getStudentId()).map(u -> primerNombre(u.getFullName())).orElse(null);
        Optional<StudentProfile> perfil = perfiles.findById(b.getStudentId());
        String nivel = perfil.map(StudentProfile::getSelfDeclaredLevel).map(Enum::name).orElse(null);
        String objetivo = perfil.map(StudentProfile::getMotivation).map(t -> TextoParaIa.unaLinea(t, 280)).orElse(null);
        return new LessonNoteDrafter.Contexto(profesor.getId(), crudo, nombre,
                "EN".equalsIgnoreCase(b.getLanguageCode()) || b.getLanguageCode() == null ? "inglés" : b.getLanguageCode(),
                nivel, objetivo);
    }

    private List<LessonVocabulary> reemplazarVocabulario(LessonNote nota, List<Palabra> palabras) {
        vocabulario.borrarDe(nota.getId());
        List<LessonVocabulary> nuevas = new ArrayList<>();
        int orden = 0;
        for (Palabra p : palabras == null ? List.<Palabra>of() : palabras) {
            if (p.term() == null || p.term().isBlank() || nuevas.size() >= 12) {
                continue;
            }
            nuevas.add(new LessonVocabulary(nota.getId(), nota.getStudentId(), nota.getLanguageCode(),
                    p.term().trim(), p.meaning() == null || p.meaning().isBlank() ? null : p.meaning().trim(),
                    orden++));
        }
        return vocabulario.saveAll(nuevas);
    }

    private List<Acta> conVocabulario(List<LessonNote> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<LessonVocabulary> todas = vocabulario.findByLessonNoteIdInOrderByDisplayOrderAsc(
                lista.stream().map(LessonNote::getId).toList());
        return lista.stream().map(n -> new Acta(n,
                todas.stream().filter(v -> v.getLessonNoteId().equals(n.getId())).toList(), false)).toList();
    }

    /** Todo el acta como un solo texto, para medir cuánto cambió entre el borrador y lo publicado. */
    static String texto(String trabajado, String presente, String sigue, List<Palabra> palabras) {
        StringBuilder sb = new StringBuilder();
        sb.append(nulo(trabajado)).append('\n').append(nulo(presente)).append('\n').append(nulo(sigue));
        for (Palabra p : palabras == null ? List.<Palabra>of() : palabras) {
            sb.append('\n').append(nulo(p.term())).append(": ").append(nulo(p.meaning()));
        }
        return sb.toString();
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }

    private static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        int espacio = nombre.trim().indexOf(' ');
        return espacio < 0 ? nombre.trim() : nombre.trim().substring(0, espacio);
    }
}
