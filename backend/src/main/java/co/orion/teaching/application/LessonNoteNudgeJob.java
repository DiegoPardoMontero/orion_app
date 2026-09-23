package co.orion.teaching.application;

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

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.teaching.domain.LessonNoteNudgeEvent;

/**
 * «¿Nos cuentas cómo estuvo tu clase con Mariana?», una sola vez por clase.
 *
 * <p>Cada 15 minutos: clases cerradas hace más de {@code lesson_note_nudge_minutes} (60), sin acta y
 * sin recordatorio. Idempotente por {@code bookings.note_nudge_sent_at}: se marca en la misma
 * transacción que publica el evento. <strong>Una vez y nunca insiste</strong>: un sistema que
 * persigue al profesor todos los días es un sistema que el profesor aprende a ignorar.
 *
 * <p>La transacción va por {@code TransactionTemplate} y no por {@code @Transactional}: el
 * programador llama a {@link #run()}, y una anotación en {@link #recordar()} no se aplica desde
 * dentro de la misma clase. Sin transacción, el aviso —que sale después del commit— se perdía y
 * la clase quedaba marcada igual.
 */
@Component
public class LessonNoteNudgeJob {

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final LessonNotesLaunch lanzamiento;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate enTransaccion;
    private final Clock clock;

    public LessonNoteNudgeJob(JdbcTemplate jdbc, PlatformSettingsService settings,
                              LessonNotesLaunch lanzamiento, ApplicationEventPublisher eventos,
                              PlatformTransactionManager transacciones, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.lanzamiento = lanzamiento;
        this.eventos = eventos;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.lesson-note-nudge.interval-ms:900000}")
    public void run() {
        recordar();
    }

    /** @return cuántos recordatorios salieron */
    public int recordar() {
        Integer enviados = enTransaccion.execute(estado -> marcarYAvisar());
        return enviados == null ? 0 : enviados;
    }

    private int marcarYAvisar() {
        Instant ahora = clock.instant();
        Instant cerradasAntesDe = ahora.minus(Duration.ofMinutes(settings.getInt("lesson_note_nudge_minutes")));
        List<Object[]> pendientes = jdbc.query("""
                select b.id, b.professor_id, b.student_id from bookings b
                where b.status = 'COMPLETED' and b.note_nudge_sent_at is null
                  and b.completed_at < ? and b.completed_at >= ?
                  and not exists (select 1 from lesson_notes n where n.booking_id = b.id)
                for update skip locked
                """, (rs, i) -> new Object[] {
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)},
                Timestamp.from(cerradasAntesDe), Timestamp.from(lanzamiento.desde()));
        for (Object[] p : pendientes) {
            jdbc.update("update bookings set note_nudge_sent_at = ? where id = ?", Timestamp.from(ahora), p[0]);
            eventos.publishEvent(new LessonNoteNudgeEvent((UUID) p[0], (UUID) p[1], (UUID) p[2]));
        }
        return pendientes.size();
    }
}
