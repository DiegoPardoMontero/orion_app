package co.orion.messaging.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.orion.messaging.domain.RigelKind;
import co.orion.shared.time.FechasEnPalabras;

/**
 * «¿Seguimos?»: al estudiante que ya tomó clases y lleva dos semanas sin ninguna ni una por venir,
 * Rigel le escribe una vez —y a lo sumo una vez al mes—, con el profe de su última clase a un botón.
 * Nada más: ni campana, ni correo, ni insistir.
 */
@Component
public class RigelTeExtrana {

    private static final Logger log = LoggerFactory.getLogger(RigelTeExtrana.class);

    /** Sin clase desde hace esto, y sin ninguna por venir. */
    static final Duration SIN_CLASE = Duration.ofDays(14);
    /** Y no más de uno en este tiempo. */
    static final Duration ENTRE_UNO_Y_OTRO = Duration.ofDays(30);

    private final JdbcTemplate jdbc;
    private final RigelService rigel;
    private final Clock clock;

    public RigelTeExtrana(JdbcTemplate jdbc, RigelService rigel, Clock clock) {
        this.jdbc = jdbc;
        this.rigel = rigel;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.rigel-come-back.interval-ms:3600000}")
    public void cadaHora() {
        try {
            recordar();
        } catch (RuntimeException ex) {
            log.warn("Rigel no pudo revisar a quién extraña: {}", ex.getMessage());
        }
    }

    /** @return a cuántos les escribió */
    public int recordar() {
        Instant ahora = clock.instant();
        // Su última clase dictada, con quién fue, y que no tenga nada por delante ni un «¿Seguimos?» reciente.
        List<Map<String, Object>> quienes = jdbc.queryForList("""
                select distinct on (b.student_id) b.student_id, b.professor_id, u.full_name as profesor
                  from bookings b
                  join users u on u.id = b.professor_id
                  join users s on s.id = b.student_id and s.status = 'ACTIVE'
                 where b.status = 'COMPLETED' and not b.is_rehearsal
                   and not exists (select 1 from bookings f
                                    where f.student_id = b.student_id
                                      and f.status in ('PENDING_PAYMENT', 'CONFIRMED', 'UNDER_REVIEW')
                                      and f.ends_at > ?)
                   and not exists (select 1 from bookings r
                                    where r.student_id = b.student_id and r.status = 'COMPLETED'
                                      and r.ends_at > ?)
                   and not exists (select 1 from rigel_messages m
                                    where m.user_id = b.student_id and m.kind = 'COME_BACK'
                                      and m.created_at > ?)
                 order by b.student_id, b.ends_at desc
                """, Timestamp.from(ahora), Timestamp.from(ahora.minus(SIN_CLASE)),
                Timestamp.from(ahora.minus(ENTRE_UNO_Y_OTRO)));
        int enviados = 0;
        for (Map<String, Object> q : quienes) {
            Map<String, String> datos = new HashMap<>();
            String profesor = FechasEnPalabras.primerNombre((String) q.get("profesor"));
            if (!profesor.isBlank()) {
                datos.put("profesor", profesor);
                datos.put("profesorId", String.valueOf(q.get("professor_id")));
            }
            if (rigel.enviar((UUID) q.get("student_id"), RigelKind.COME_BACK, datos)) {
                enviados++;
            }
        }
        return enviados;
    }
}
