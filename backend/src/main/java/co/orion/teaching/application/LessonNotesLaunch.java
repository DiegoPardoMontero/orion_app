package co.orion.teaching.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Desde cuándo existen las actas: la fecha en que se aplicó la V48, que Flyway ya guarda.
 *
 * <p>D4 del brief: sin actas retroactivas. Las clases cerradas antes de esa fecha no admiten acta
 * ni reciben recordatorio; de otro modo, el primer día llegarían decenas de avisos por clases de
 * hace meses que nadie recuerda.
 */
@Component
public class LessonNotesLaunch {

    private final JdbcTemplate jdbc;
    private volatile Instant desde;

    public LessonNotesLaunch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Instant desde() {
        Instant cache = desde;
        if (cache == null) {
            List<Timestamp> r = jdbc.queryForList(
                    "select installed_on from flyway_schema_history where version = '48'", Timestamp.class);
            cache = r.isEmpty() ? Instant.EPOCH : r.getFirst().toInstant();
            desde = cache;
        }
        return cache;
    }
}
