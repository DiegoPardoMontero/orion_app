package co.orion.lifecycle.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.lifecycle.domain.ClassReminderDueEvent;
import co.orion.lifecycle.domain.ClassReminderKind;
import co.orion.shared.observability.JobRunRegistry;

/**
 * Los recordatorios de clase (24/09/2026: «recordar clases, recordar calificar… sin ser demasiado
 * invasivo»). Tres por clase como mucho, cada uno una sola vez:
 *
 * <ul>
 *   <li><strong>El día antes</strong>, cuando falten menos de 24 horas. No a quien reservó con
 *       menos de un día: acaba de recibir la confirmación y no necesita que se la repitan.
 *   <li><strong>Una hora antes</strong>. No a quien reservó hace menos de un cuarto de hora.
 *   <li><strong>Calificar</strong>, un día después de una clase dictada que sigue sin reseña. Solo
 *       para las de los últimos tres días: el día que esto se despliega no le llueven a nadie
 *       recordatorios de clases de hace un mes.
 * </ul>
 *
 * <p>Este trabajo solo decide qué toca y lo marca; qué se dice y por dónde (campana, correo,
 * dispositivo) lo deciden los que escuchan el evento. La transacción va por
 * {@code TransactionTemplate} por la misma trampa de siempre: el programador llama al método desde
 * fuera, y una anotación en un método interno no se aplicaría.
 */
@Component
public class RecordatorioDeClases {

    public static final String JOB_NAME = "class-reminders";

    private static final Duration UN_DIA = Duration.ofDays(1);
    private static final Duration MARGEN_DEL_DIA_ANTES = Duration.ofHours(2);
    private static final Duration UNA_HORA = Duration.ofHours(1);
    private static final Duration RESERVADA_HACE = Duration.ofMinutes(15);
    private static final Duration CALIFICAR_DESDE = Duration.ofHours(25);
    private static final Duration CALIFICAR_HASTA = Duration.ofDays(3);

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate enTransaccion;
    private final JobRunRegistry runs;
    private final Clock clock;

    public RecordatorioDeClases(JdbcTemplate jdbc, ApplicationEventPublisher eventos,
                                PlatformTransactionManager transacciones, JobRunRegistry runs, Clock clock) {
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.runs = runs;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.class-reminders.interval-ms:300000}")
    public void run() {
        try {
            int salieron = recordar();
            runs.recordSuccess(JOB_NAME, clock.instant(), salieron + " recordatorio(s)");
        } catch (RuntimeException ex) {
            runs.recordFailure(JOB_NAME, clock.instant(), ex.getMessage());
            throw ex;
        }
    }

    /** @return cuántos recordatorios salieron en esta corrida */
    public int recordar() {
        Instant ahora = clock.instant();
        int salieron = 0;

        // Las de prueba del admin no se recuerdan: son ensayos del aula, no clases de nadie.
        for (UUID id : jdbc.queryForList("""
                select b.id from bookings b
                 where b.status = 'CONFIRMED' and not b.is_trial
                   and b.starts_at > ? and b.starts_at <= ?
                   and b.created_at <= b.starts_at - interval '24 hours'
                   and not exists (select 1 from booking_reminders r where r.booking_id = b.id and r.kind = 'DAY_BEFORE')
                """, UUID.class, ts(ahora.plus(MARGEN_DEL_DIA_ANTES)), ts(ahora.plus(UN_DIA)))) {
            salieron += marcar(id, ClassReminderKind.DAY_BEFORE, ahora);
        }

        for (UUID id : jdbc.queryForList("""
                select b.id from bookings b
                 where b.status = 'CONFIRMED' and not b.is_trial
                   and b.starts_at > ? and b.starts_at <= ?
                   and b.created_at <= ?
                   and not exists (select 1 from booking_reminders r where r.booking_id = b.id and r.kind = 'HOUR_BEFORE')
                """, UUID.class, ts(ahora), ts(ahora.plus(UNA_HORA)), ts(ahora.minus(RESERVADA_HACE)))) {
            salieron += marcar(id, ClassReminderKind.HOUR_BEFORE, ahora);
        }

        for (UUID id : jdbc.queryForList("""
                select b.id from bookings b
                 where b.status = 'COMPLETED' and not b.is_trial
                   and b.starts_at <= ? and b.starts_at > ?
                   and not exists (select 1 from reviews v where v.booking_id = b.id)
                   and not exists (select 1 from booking_reminders r where r.booking_id = b.id and r.kind = 'RATE')
                """, UUID.class, ts(ahora.minus(CALIFICAR_DESDE)), ts(ahora.minus(CALIFICAR_HASTA)))) {
            salieron += marcar(id, ClassReminderKind.RATE, ahora);
        }
        return salieron;
    }

    /** Marca y publica en la misma transacción: los que escuchan salen solo si la marca entró. */
    private int marcar(UUID bookingId, ClassReminderKind kind, Instant ahora) {
        Boolean salio = enTransaccion.execute(estado -> {
            int filas = jdbc.update("""
                    insert into booking_reminders (booking_id, kind, sent_at) values (?, ?, ?)
                    on conflict do nothing
                    """, bookingId, kind.name(), ts(ahora));
            if (filas == 0) {
                return false;
            }
            eventos.publishEvent(new ClassReminderDueEvent(bookingId, kind));
            return true;
        });
        return Boolean.TRUE.equals(salio) ? 1 : 0;
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
