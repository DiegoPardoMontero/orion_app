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
 * La práctica (brief del Bloque 10, paso B3). El estudiante opera sobre sus sets; el profesor ve el
 * resumen de sus estudiantes y, en cada acta, los ejercicios con lo que hizo su estudiante.
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
        // Con los ejercicios, ya cerrados: la constelación de cada set —en «Mi cielo» y al pie del
        // resumen de la clase— se pinta con lo que pasó en cada estrella.
        return practica.historial(principal.user()).stream().map(c -> vista(c)).toList();
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

    /** Unir un par en Parejas: el que va se queda unido; el que no va gasta un intento. */
    @PostMapping("/api/v1/practice-items/{id}/pair")
    public PairView unirPareja(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id,
                               @Valid @RequestBody ParejaRequest body) {
        PracticeService.ResultadoDePareja r = practica.unirPareja(principal.user(), id, body.term(), body.meaning());
        return new PairView(r.va(), r.cerrado(), r.intentosQueQuedan(), item(r.ejercicio(), r.cerrado()));
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
     * Los ejercicios de un acta, para el profesor que la escribió, con lo que hizo su estudiante en
     * cada uno (decisión de Pardo, 24/09/2026; el estudiante lo sabe desde su portada). 204 si el
     * acta todavía no tiene práctica.
     */
    @GetMapping("/api/v1/professors/me/lesson-notes/{id}/practice")
    public ResponseEntity<PracticaDelActa> delActa(@AuthenticationPrincipal OrionUserDetails principal,
                                                   @PathVariable UUID id) {
        int max = practica.maxIntentos();
        return practica.delActa(principal.user(), id)
                .map(c -> {
                    PracticeSet s = c.set();
                    List<PracticeItem> ej = c.ejercicios();
                    String estudiante = users.findById(s.getStudentId()).map(u -> u.getFullName()).orElse(null);
                    return ResponseEntity.ok(new PracticaDelActa(s.getId(), s.getStatus().name(), s.getItemCount(),
                            bogota(s.getExpiresAt()), bogota(s.getCompletedAt()), estudiante,
                            (int) ej.stream().filter(PracticeItem::alPrimerIntento).count(),
                            (int) ej.stream().filter(PracticeItem::alSegundoIntento).count(),
                            (int) ej.stream().filter(i -> i.cerrado(max) && Boolean.FALSE.equals(i.getCorrect())).count(),
                            (int) ej.stream().filter(i -> i.getSkippedAt() != null).count(),
                            ej.stream().map(i -> new EjercicioDelActa(i.getItemIndex(), i.getItemType().name(),
                                    i.getItemType().categoria().name(), i.getPrompt(), i.getPayload(), i.getExpected(),
                                    i.getExplanation(), i.getSourceTerm(), i.getFirstAnswer(),
                                    i.getAttempts() > 1 ? i.getAnswer() : null, i.getAttempts(), i.getCorrect(),
                                    i.getSkippedAt() != null, i.cerrado(max))).toList()));
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Las prácticas de un estudiante con este profesor, para su ficha. */
    @GetMapping("/api/v1/professors/me/students/{id}/practice-sets")
    public List<PracticaEnLaFicha> historialParaElProfesor(@AuthenticationPrincipal OrionUserDetails principal,
                                                          @PathVariable UUID id) {
        int max = practica.maxIntentos();
        return practica.historialParaElProfesor(principal.user(), id).stream().map(c -> {
            PracticeSet s = c.set();
            Material m = material(s);
            return new PracticaEnLaFicha(s.getId(), s.getLessonNoteId(), m == null ? null : m.bookingId(),
                    m == null || m.classStartsAt() == null ? null
                            : ZonedDateTime.ofInstant(java.time.Instant.parse(m.classStartsAt()), BusinessZone.BOGOTA),
                    s.getStatus().name(), s.getItemCount(), s.getCorrectCount(), bogota(s.getCompletedAt()),
                    m == null ? null : m.workedOn(), c.ejercicios().stream().map(i -> estrella(i, max)).toList());
        }).toList();
    }

    /* ---------------- vistas ---------------- */

    /**
     * Un ejercicio del acta con lo que hizo el estudiante: su primera respuesta, la del segundo
     * intento si lo hubo, si acertó y si lo saltó.
     */
    public record EjercicioDelActa(int index, String type, String category, String prompt, String payload,
                                   String expected, String explanation, String sourceTerm, String firstAnswer,
                                   String secondAnswer, int attempts, Boolean correct, boolean skipped,
                                   boolean closed) {
    }

    /** El set de un acta con su resumen: cuántos al primer intento, al segundo, mostrados y saltados. */
    public record PracticaDelActa(UUID id, String status, int itemCount, ZonedDateTime expiresAt,
                                  ZonedDateTime completedAt, String studentName, int firstTry, int secondTry,
                                  int shown, int skipped, List<EjercicioDelActa> items) {
    }

    /**
     * @param stars cómo quedó cada estrella del set, en orden: {@code primero}, {@code segundo},
     *              {@code mostrada}, {@code saltada} u {@code off}
     */
    public record PracticaEnLaFicha(UUID id, UUID lessonNoteId, String bookingId, ZonedDateTime classStartsAt,
                                    String status, int itemCount, int correctCount, ZonedDateTime completedAt,
                                    String workedOn, List<String> stars) {
    }

    /** La estrella de un ejercicio en la constelación del set, como la pinta el estudiante. */
    static String estrella(PracticeItem i, int max) {
        if (i.getSkippedAt() != null) {
            return "saltada";
        }
        if (Boolean.TRUE.equals(i.getCorrect())) {
            return i.getAttempts() <= 1 ? "primero" : "segundo";
        }
        return i.cerrado(max) ? "mostrada" : "off";
    }

    public record RespuestaRequest(@NotNull @Size(max = 600) String answer) {
    }

    /**
     * @param hint        la pista del «Casi…»: viaja solo tras un fallo con el ejercicio aún abierto
     * @param explanation viaja con el ejercicio cerrado (acertado, mostrado o saltado)
     */
    public record ItemView(UUID id, int index, String type, String category, String prompt, String payload,
                           int attempts, Boolean correct, boolean closed, boolean skipped, String hint,
                           String explanation, String expected, String answer) {
    }

    public record SetView(UUID id, String status, UUID lessonNoteId, String bookingId, ZonedDateTime classStartsAt,
                          String professorName, String workedOn, int vocabularyCount, Integer estimatedMinutes,
                          int itemCount, int correctCount, ZonedDateTime expiresAt, ZonedDateTime completedAt,
                          List<ItemView> items, boolean perfect) {
    }

    public record ResultView(boolean correct, boolean closed, int attemptsLeft, ItemView item) {
    }

    public record ParejaRequest(@NotNull @Size(max = 120) String term, @NotNull @Size(max = 300) String meaning) {
    }

    /** @param pairCorrect si ese par iba */
    public record PairView(boolean pairCorrect, boolean closed, int attemptsLeft, ItemView item) {
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
                c.ejercicios().stream().map(i -> item(i, i.cerrado(practica.maxIntentos()))).toList(),
                // Constelación perfecta: todo lo respondido, al primer intento. El cierre muestra su bono.
                PracticeItem.perfecta(c.ejercicios()));
    }

    /**
     * Tras un fallo va la pista del «Casi…» (brief, B5.2); cerrado, la explicación y la respuesta
     * esperada. Acertado también: en «caza el error» es la frase bien dicha que se muestra al acertar.
     */
    private static ItemView item(PracticeItem i, boolean cerrado) {
        // Tras un fallo, con el ejercicio abierto, va la pista (o la explicación, en los sets de antes de
        // la pista). Cerrado —acertado, mostrado o saltado—, la explicación.
        boolean casi = !cerrado && i.getAttempts() > 0;
        return new ItemView(i.getId(), i.getItemIndex(), i.getItemType().name(), i.getItemType().categoria().name(),
                i.getPrompt(), payload(i, cerrado), i.getAttempts(), i.getCorrect(), cerrado, i.getSkippedAt() != null,
                casi ? (i.getHint() != null ? i.getHint() : i.getExplanation()) : null,
                cerrado ? i.getExplanation() : null, cerrado ? i.getExpected() : null, i.getAnswer());
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
