package co.orion.practice.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.practice.domain.PracticeReminderDueEvent;

/**
 * Recordar la práctica sin agobiar (24/09/2026): una sola vez, a los dos días de estar lista, si el
 * estudiante no la ha empezado y le quedan al menos doce horas. Va a la campana y no suena en el
 * dispositivo: el aviso de «lista» ya sonó.
 *
 * <p>La marca se pone con un UPDATE condicionado ({@code reminded_at is null}), así que dos corridas
 * o dos instancias a la vez no recuerdan dos veces.
 */
@Component
public class RecordatorioDePractica {

    private static final Duration DESPUES_DE = Duration.ofDays(2);
    private static final Duration LE_QUEDA = Duration.ofHours(12);

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate enTransaccion;
    private final Clock clock;

    public RecordatorioDePractica(JdbcTemplate jdbc, ApplicationEventPublisher eventos,
                                  PlatformTransactionManager transacciones, Clock clock) {
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.practice-reminder.interval-ms:3600000}")
    public void run() {
        recordar();
    }

    /** @return cuántas prácticas se recordaron */
    public int recordar() {
        Instant ahora = clock.instant();
        List<UUID> pendientes = jdbc.queryForList("""
                select id from practice_sets
                 where status = 'READY' and reminded_at is null
                   and created_at <= ? and expires_at > ?
                """, UUID.class, Timestamp.from(ahora.minus(DESPUES_DE)), Timestamp.from(ahora.plus(LE_QUEDA)));
        int recordadas = 0;
        for (UUID id : pendientes) {
            Boolean salio = enTransaccion.execute(estado -> {
                List<Object[]> fila = jdbc.query("""
                        update practice_sets set reminded_at = ? where id = ? and reminded_at is null
                        returning student_id, professor_id, item_count
                        """, (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                                rs.getInt(3)}, Timestamp.from(ahora), id);
                if (fila.isEmpty()) {
                    return false;
                }
                Object[] f = fila.getFirst();
                eventos.publishEvent(new PracticeReminderDueEvent(id, (UUID) f[0], (UUID) f[1], (Integer) f[2]));
                return true;
            });
            if (Boolean.TRUE.equals(salio)) {
                recordadas++;
            }
        }
        return recordadas;
    }
}
