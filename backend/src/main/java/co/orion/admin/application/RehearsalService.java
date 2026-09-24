package co.orion.admin.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import co.orion.catalog.application.PlatformSettingsService;

/**
 * En qué va cada ensayo del acta y la práctica (Bloque 10): la clase de prueba ya dictada, su acta
 * y el set de ejercicios que salió de ella.
 *
 * <p>Existe porque el recorrido cruza tres pantallas y dos cuentas, y lo que falla —la práctica
 * que no se genera, una función apagada en Ajustes— no se ve desde ninguna de ellas. Una consulta
 * de solo lectura, sin repositorios de otros módulos: el admin mira, no toca.
 */
@Service
public class RehearsalService {

    private static final Duration CUANTO_ATRAS = Duration.ofDays(7);

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final String voiceProvider;
    private final Clock clock;

    public RehearsalService(JdbcTemplate jdbc, PlatformSettingsService settings,
                            @Value("${orion.assessment.voice.provider:scripted}") String voiceProvider,
                            Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.voiceProvider = voiceProvider;
        this.clock = clock;
    }

    /**
     * @param acta        {@code null} si todavía no hay; si no, DRAFT o PUBLISHED
     * @param practica    {@code null} si no hay set; si no, su estado (PENDING, READY, …, FAILED)
     */
    public record Ensayo(UUID bookingId, Instant cerrada, String estudiante, String estudianteEmail,
                         String profesor, String profesorEmail, String acta, boolean actaConIa,
                         UUID practicaId, String practica, int ejercicios, int correctos, int intentos) {
    }

    /** Lo que impediría ver el recorrido completo, dicho antes de que alguien lo descubra a medias. */
    public record Ensayos(List<Ensayo> ensayos, List<String> avisos) {
    }

    public Ensayos recientes() {
        List<Ensayo> ensayos = jdbc.query("""
                select b.id, b.completed_at,
                       s.full_name as estudiante, s.email as estudiante_email,
                       p.full_name as profesor, p.email as profesor_email,
                       n.status as acta, n.original_draft is not null as con_ia,
                       ps.id as practica_id, ps.status as practica,
                       coalesce(ps.item_count, 0) as ejercicios, coalesce(ps.correct_count, 0) as correctos,
                       coalesce(ps.generation_attempts, 0) as intentos
                  from bookings b
                  join users s on s.id = b.student_id
                  join users p on p.id = b.professor_id
                  left join lesson_notes n on n.booking_id = b.id
                  left join practice_sets ps on ps.lesson_note_id = n.id
                 where b.is_rehearsal and b.status = 'COMPLETED' and b.completed_at >= ?
                 order by b.completed_at desc
                 limit 6
                """, (rs, i) -> new Ensayo(
                rs.getObject("id", UUID.class), rs.getTimestamp("completed_at").toInstant(),
                rs.getString("estudiante"), rs.getString("estudiante_email"),
                rs.getString("profesor"), rs.getString("profesor_email"),
                rs.getString("acta"), rs.getBoolean("con_ia"),
                rs.getObject("practica_id", UUID.class), rs.getString("practica"),
                rs.getInt("ejercicios"), rs.getInt("correctos"), rs.getInt("intentos")),
                Timestamp.from(clock.instant().minus(CUANTO_ATRAS)));
        return new Ensayos(ensayos, avisos());
    }

    private List<String> avisos() {
        List<String> avisos = new ArrayList<>();
        if (!settings.getBoolean("ai_lesson_notes_enabled")) {
            avisos.add("El borrador con IA está apagado en Ajustes: el profesor escribirá el acta a mano.");
        }
        if (!settings.getBoolean("practice_enabled")) {
            avisos.add("La práctica está apagada en Ajustes: publicar el acta no generará ejercicios.");
        }
        if (!"openai".equalsIgnoreCase(voiceProvider)) {
            avisos.add("Este despliegue no usa OpenAI: el borrador y los ejercicios salen del generador "
                    + "local, que solo reusa lo que el acta dice literal.");
        }
        return avisos;
    }
}
