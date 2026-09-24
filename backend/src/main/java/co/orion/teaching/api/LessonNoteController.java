package co.orion.teaching.api;

import java.io.IOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.security.IntentosDeAcceso;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;
import co.orion.teaching.application.DictadoService;
import co.orion.teaching.application.LessonNoteDrafter.Palabra;
import co.orion.teaching.application.LessonNoteService;
import co.orion.teaching.application.LessonNoteService.Acta;
import co.orion.teaching.domain.LessonNote;
import co.orion.teaching.domain.LessonVocabulary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El acta de clase (Bloque 10, paso A3).
 *
 * <p><strong>Dos vistas, a propósito.</strong> El profesor recibe todo lo suyo. El estudiante
 * recibe el acta sin {@code raw_input}, sin {@code edit_ratio}, sin {@code origin} y sin
 * {@code prompt_version}: lo que el profesor escribió en crudo es su cuaderno privado, y cuánto
 * corrigió a la IA no es asunto del estudiante. No es que la pantalla no los pinte: es que no
 * viajan.
 */
@RestController
public class LessonNoteController {

    private final LessonNoteService actas;
    private final DictadoService dictado;
    private final IntentosDeAcceso intentos;
    private final UserRepository users;
    private final Clock clock;

    public LessonNoteController(LessonNoteService actas, DictadoService dictado, IntentosDeAcceso intentos,
                                UserRepository users, Clock clock) {
        this.actas = actas;
        this.dictado = dictado;
        this.intentos = intentos;
        this.users = users;
        this.clock = clock;
    }

    @PostMapping("/api/v1/bookings/{id}/lesson-note/draft")
    public ActaDelProfesor draft(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id,
                                 @Valid @RequestBody RedactarRequest body) {
        return delProfesor(actas.redactar(principal.user(), id, body.rawInput().trim()));
    }

    /**
     * El profesor dicta en vez de escribir: el audio va al proveedor, vuelve el texto y el audio no
     * se guarda. El texto cae en la caja para que lo revise antes de generar el acta.
     */
    @PostMapping(value = "/api/v1/bookings/{id}/lesson-note/dictation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> dictar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id,
                                      @RequestParam("file") MultipartFile audio,
                                      @RequestParam("seconds") int segundos) throws IOException {
        intentos.antesDeDictar(principal.user().getId());
        return Map.of("text", dictado.dictar(principal.user(), id, audio.getBytes(), audio.getContentType(), segundos));
    }

    /** El acta de una clase, en la vista que le corresponde a quien la pide. */
    @GetMapping("/api/v1/bookings/{id}/lesson-note")
    public ResponseEntity<?> deLaClase(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        Acta acta = actas.deLaClase(principal.user(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no hay acta de esta clase."));
        boolean esProfesor = acta.nota().getProfessorId().equals(principal.user().getId());
        return ResponseEntity.ok(esProfesor ? delProfesor(acta) : delEstudiante(acta));
    }

    @PutMapping("/api/v1/lesson-notes/{id}")
    public ActaDelProfesor guardar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id,
                                   @Valid @RequestBody GuardarRequest body) {
        return delProfesor(actas.guardar(principal.user(), id, body.workedOn(), body.recurringIssues(),
                body.nextSteps(), body.vocabulary() == null ? List.of()
                        : body.vocabulary().stream().map(p -> new Palabra(p.term(), p.meaning())).toList()));
    }

    @PostMapping("/api/v1/lesson-notes/{id}/publish")
    public ActaDelProfesor publicar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        return delProfesor(actas.publicar(principal.user(), id));
    }

    /**
     * Las actas de quien pregunta: el profesor, las suyas (o solo los borradores con
     * {@code status=DRAFT}); el estudiante, las publicadas, de veinte en veinte.
     */
    @GetMapping("/api/v1/me/lesson-notes")
    public List<?> mias(@AuthenticationPrincipal OrionUserDetails principal,
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") int page) {
        if ("PROFESSOR".equals(principal.rolEfectivo())) {
            return actas.delProfesor(principal.user(), "DRAFT".equalsIgnoreCase(status)).stream()
                    .map(this::delProfesor).toList();
        }
        return actas.delEstudiante(principal.user(), page).stream().map(this::delEstudiante).toList();
    }

    /**
     * Lo que la lista de clases necesita para pintar cada tarjeta: el estado del acta de cada
     * clase (al profesor, también {@code PENDING}: cerrada y todavía sin acta) y desde cuándo
     * existen las actas.
     */
    @GetMapping("/api/v1/me/lesson-notes/summary")
    public ResumenDeActas resumen(@AuthenticationPrincipal OrionUserDetails principal) {
        boolean comoProfesor = "PROFESSOR".equals(principal.rolEfectivo());
        return new ResumenDeActas(bogota(actas.desde()), actas.estados(principal.user(), comoProfesor));
    }

    public record ResumenDeActas(ZonedDateTime since, Map<UUID, String> byBooking) {
    }

    /**
     * La lista de actas: al profesor, las que le faltan (cerradas sin acta y borradores); al
     * estudiante, las publicadas. Solo lo que una lista necesita: la clase, con quién y el estado.
     */
    @GetMapping("/api/v1/me/lesson-notes/index")
    public List<EntradaView> indice(@AuthenticationPrincipal OrionUserDetails principal) {
        List<LessonNoteService.Entrada> entradas =
                actas.indice(principal.user(), "PROFESSOR".equals(principal.rolEfectivo()));
        Map<UUID, String> nombres = new HashMap<>();
        users.findAllById(entradas.stream().map(LessonNoteService.Entrada::contraparteId).distinct().toList())
                .forEach(u -> nombres.put(u.getId(), u.getFullName()));
        return entradas.stream()
                .map(e -> new EntradaView(e.bookingId(), bogota(e.claseEmpieza()),
                        nombres.get(e.contraparteId()), e.estado(), bogota(e.publicada())))
                .toList();
    }

    public record EntradaView(UUID bookingId, ZonedDateTime classStartsAt, String counterpartName,
                              String status, ZonedDateTime publishedAt) {
    }

    /* ---------------- vistas ---------------- */

    public record PalabraView(String term, String meaning) {
    }

    public record ActaDelProfesor(UUID id, UUID bookingId, String status, String rawInput, String workedOn,
                                  String recurringIssues, String nextSteps, List<PalabraView> vocabulary,
                                  String origin, boolean draftedByAi, boolean editable,
                                  ZonedDateTime publishedAt, ZonedDateTime lastEditedAt, String studentName,
                                  ZonedDateTime classStartsAt, ZonedDateTime editableUntil) {
    }

    /**
     * Sin crudo, sin edit_ratio, sin origen, sin versión del prompt. Con el profesor, eso sí: el acta
     * no se responde, se le escribe por la mensajería (brief, D3), y para eso hace falta a quién.
     */
    public record ActaDelEstudiante(UUID id, UUID bookingId, UUID professorId, String professorName, String workedOn,
                                    String recurringIssues, String nextSteps, List<PalabraView> vocabulary,
                                    ZonedDateTime publishedAt, ZonedDateTime lastEditedAt) {
    }

    private ActaDelProfesor delProfesor(Acta a) {
        LessonNote n = a.nota();
        return new ActaDelProfesor(n.getId(), n.getBookingId(), n.getStatus().name(), n.getRawInput(),
                n.getWorkedOn(), n.getRecurringIssues(), n.getNextSteps(), palabras(a.palabras()),
                n.getOrigin().name(), a.propuestaPorIa(), n.editable(actas.ventana(), clock.instant()),
                bogota(n.getPublishedAt()), bogota(n.getLastEditedAt()),
                users.findById(n.getStudentId()).map(u -> u.getFullName()).orElse(null),
                bogota(actas.empiezaLaClase(n.getBookingId())),
                // Publicada, se corrige durante la ventana (hoy 72 h): la pantalla dice hasta cuándo.
                n.getPublishedAt() == null ? null : bogota(n.getPublishedAt().plus(actas.ventana())));
    }

    private ActaDelEstudiante delEstudiante(Acta a) {
        LessonNote n = a.nota();
        String profe = users.findById(n.getProfessorId()).map(u -> u.getFullName()).orElse(null);
        return new ActaDelEstudiante(n.getId(), n.getBookingId(), n.getProfessorId(), profe, n.getWorkedOn(),
                n.getRecurringIssues(), n.getNextSteps(), palabras(a.palabras()),
                bogota(n.getPublishedAt()), bogota(n.getLastEditedAt()));
    }

    private static List<PalabraView> palabras(List<LessonVocabulary> lista) {
        return lista.stream().map(v -> new PalabraView(v.getTerm(), v.getMeaning())).toList();
    }

    private static ZonedDateTime bogota(java.time.Instant i) {
        return i == null ? null : ZonedDateTime.ofInstant(i, BusinessZone.BOGOTA);
    }

    /* ---------------- peticiones ---------------- */

    /** Mínimo 20 caracteres: con menos no hay nada que ordenar (brief, A5.1). */
    public record RedactarRequest(
            @NotBlank @Size(min = 20, max = LessonNote.MAX_CRUDO,
                    message = "Escribe al menos 20 caracteres sobre la clase.") String rawInput) {
    }

    public record PalabraRequest(@NotBlank @Size(max = 120) String term, @Size(max = 300) String meaning) {
    }

    public record GuardarRequest(
            @Size(max = LessonNote.MAX_SECCION) String workedOn,
            @Size(max = LessonNote.MAX_SECCION) String recurringIssues,
            @Size(max = LessonNote.MAX_SECCION) String nextSteps,
            @Size(max = 12, message = "Máximo 12 palabras nuevas por clase.") List<@Valid PalabraRequest> vocabulary) {
    }
}
