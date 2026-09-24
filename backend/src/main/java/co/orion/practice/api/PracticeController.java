package co.orion.practice.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import co.orion.identity.persistence.UserRepository;
import co.orion.practice.application.Material;
import co.orion.practice.application.PracticeMetrics;
import co.orion.practice.application.PracticeService;
import co.orion.practice.application.PracticeService.ConEjercicios;
import co.orion.practice.domain.PracticeItem;
import co.orion.practice.domain.PracticeItemType;
import co.orion.practice.domain.PracticeSet;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * La práctica (brief del Bloque 10, paso B3). El estudiante opera sobre sus sets; el profesor ve
 * un resumen agregado de sus estudiantes y los ejercicios de sus actas, nunca las respuestas.
 *
 * <p><strong>La respuesta esperada no viaja</strong> mientras el ejercicio está abierto: se manda
 * solo cuando ya está cerrado (acertado o sin intentos), para mostrarla con su explicación.
 */
@RestController
public class PracticeController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PracticeService practica;
    private final PracticeMetrics metricas;
    private final UserRepository users;

    public PracticeController(PracticeService practica, PracticeMetrics metricas, UserRepository users) {
        this.practica = practica;
        this.metricas = metricas;
        this.users = users;
    }

    /** Solo admin: la ruta cuelga de {@code /api/v1/admin/**}. */
    @GetMapping("/api/v1/admin/practice/metrics")
    public PracticeMetrics.Panel panel() {
        return metricas.panel();
    }

    /** El set vivo del estudiante; 204 si no tiene: la invitación simplemente no aparece. */
    @GetMapping("/api/v1/me/practice")
    public ResponseEntity<SetView> activo(@AuthenticationPrincipal OrionUserDetails principal) {
        return practica.activo(principal.user()).map(c -> ResponseEntity.ok(vista(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/v1/me/practice/history")
    public List<SetView> historial(@AuthenticationPrincipal OrionUserDetails principal) {
        return practica.historial(principal.user()).stream()
                .map(s -> vista(new ConEjercicios(s, List.of()))).toList();
    }

    @GetMapping("/api/v1/practice-sets/{id}")
    public SetView uno(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        return vista(practica.uno(principal.user(), id));
    }

    @PostMapping("/api/v1/practice-sets/{id}/start")
    public SetView empezar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        return vista(practica.empezar(principal.user(), id));
    }

    @PostMapping("/api/v1/practice-items/{id}/answer")
    public ResultView responder(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id,
                                @Valid @RequestBody RespuestaRequest body) {
        PracticeService.Resultado r = practica.responder(principal.user(), id, body.answer());
        return new ResultView(r.correcto(), r.cerrado(), r.intentosQueQuedan(), item(r.ejercicio(), r.cerrado()));
    }

    /** Saltar un ejercicio de escucha: el dispositivo no tiene voz en inglés. No cuenta como error. */
    @PostMapping("/api/v1/practice-items/{id}/skip")
    public ItemView saltar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        return item(practica.saltar(principal.user(), id), true);
    }

    @PostMapping("/api/v1/practice-sets/{id}/complete")
    public SetView completar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        return vista(practica.completar(principal.user(), id));
    }

    /** Resumen agregado para el profesor: cuántas practicó y dónde le costó. Nunca las respuestas. */
    @GetMapping("/api/v1/professors/me/students/{id}/practice")
    public PracticeService.Resumen paraElProfesor(@AuthenticationPrincipal OrionUserDetails principal,
                                                  @PathVariable UUID id) {
        return practica.paraElProfesor(principal.user(), id);
    }

    /**
     * Los ejercicios de un acta, para el profesor que la escribió. Aquí la respuesta esperada sí
     * viaja —el profesor no está resolviendo nada— y lo del estudiante no viaja nunca. 204 si el
     * acta todavía no tiene práctica.
     */
    @GetMapping("/api/v1/professors/me/lesson-notes/{id}/practice")
    public ResponseEntity<PracticaDelActa> delActa(@AuthenticationPrincipal OrionUserDetails principal,
                                                   @PathVariable UUID id) {
        return practica.delActa(principal.user(), id)
                .map(c -> ResponseEntity.ok(new PracticaDelActa(c.set().getId(), c.set().getStatus().name(),
                        c.set().getItemCount(), bogota(c.set().getExpiresAt()),
                        c.ejercicios().stream().map(i -> new EjercicioDelActa(i.getItemIndex(),
                                i.getItemType().name(), i.getPrompt(), i.getPayload(), i.getExpected(),
                                i.getExplanation(), i.getSourceTerm())).toList())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /* ---------------- vistas ---------------- */

    /** Sin intentos, sin acierto y sin respuesta: nada de lo que hizo el estudiante. */
    public record EjercicioDelActa(int index, String type, String prompt, String payload, String expected,
                                   String explanation, String sourceTerm) {
    }

    public record PracticaDelActa(UUID id, String status, int itemCount, ZonedDateTime expiresAt,
                                  List<EjercicioDelActa> items) {
    }

    public record RespuestaRequest(@NotNull @Size(max = 600) String answer) {
    }

    public record ItemView(UUID id, int index, String type, String category, String prompt, String payload,
                           int attempts, Boolean correct, boolean closed, boolean skipped, String explanation,
                           String expected, String answer) {
    }

    public record SetView(UUID id, String status, UUID lessonNoteId, String bookingId, ZonedDateTime classStartsAt,
                          String professorName, String workedOn, int vocabularyCount, Integer estimatedMinutes,
                          int itemCount, int correctCount, ZonedDateTime expiresAt, ZonedDateTime completedAt,
                          List<ItemView> items) {
    }

    public record ResultView(boolean correct, boolean closed, int attemptsLeft, ItemView item) {
    }

    private SetView vista(ConEjercicios c) {
        PracticeSet s = c.set();
        Material m = material(s);
        String profe = users.findById(s.getProfessorId()).map(u -> u.getFullName()).orElse(null);
        return new SetView(s.getId(), s.getStatus().name(), s.getLessonNoteId(), m == null ? null : m.bookingId(),
                m == null || m.classStartsAt() == null ? null
                        : ZonedDateTime.ofInstant(java.time.Instant.parse(m.classStartsAt()), BusinessZone.BOGOTA),
                profe, m == null ? null : m.workedOn(), m == null ? 0 : m.vocabulary().size(),
                s.getEstimatedMinutes() == null ? null : s.getEstimatedMinutes().intValue(), s.getItemCount(),
                s.getCorrectCount(), bogota(s.getExpiresAt()), bogota(s.getCompletedAt()),
                c.ejercicios().stream().map(i -> item(i, i.cerrado(practica.maxIntentos()))).toList());
    }

    /**
     * La explicación viaja en cuanto hubo un intento: es la pista del «Casi…» antes de intentar otra
     * vez (brief, B5.2). La respuesta esperada, solo con el ejercicio cerrado sin acertar.
     */
    private static ItemView item(PracticeItem i, boolean cerrado) {
        boolean acerto = Boolean.TRUE.equals(i.getCorrect());
        // Saltado también muestra la explicación: el estudiante no llegó a intentarlo, pero la merece.
        boolean mostrarExplicacion = i.getAttempts() > 0 || i.getSkippedAt() != null;
        return new ItemView(i.getId(), i.getItemIndex(), i.getItemType().name(), i.getItemType().categoria().name(),
                i.getPrompt(), payload(i, cerrado), i.getAttempts(), i.getCorrect(), cerrado, i.getSkippedAt() != null,
                mostrarExplicacion ? i.getExplanation() : null, cerrado && !acerto ? i.getExpected() : null,
                i.getAnswer());
    }

    /**
     * En corregir la frase, {@code accepted} son otras correcciones igual de válidas: respuestas, y por
     * la misma regla que {@code expected} no viajan mientras el ejercicio sigue abierto.
     */
    private static String payload(PracticeItem i, boolean cerrado) {
        if (cerrado || i.getItemType() != PracticeItemType.FIX_SENTENCE) {
            return i.getPayload();
        }
        try {
            JsonNode p = JSON.readTree(i.getPayload());
            if (p instanceof ObjectNode objeto) {
                objeto.remove("accepted");
                return JSON.writeValueAsString(objeto);
            }
            return i.getPayload();
        } catch (Exception ex) {
            return i.getPayload();
        }
    }

    private static Material material(PracticeSet s) {
        try {
            return JSON.readValue(s.getMaterial(), Material.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static ZonedDateTime bogota(java.time.Instant i) {
        return i == null ? null : ZonedDateTime.ofInstant(i, BusinessZone.BOGOTA);
    }
}
