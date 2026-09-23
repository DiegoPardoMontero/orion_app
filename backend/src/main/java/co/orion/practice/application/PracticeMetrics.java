package co.orion.practice.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;

/**
 * Las cifras de la práctica en el panel del admin (brief del Bloque 10, paso C1): cuántos sets se
 * generaron, se completaron y vencieron en los últimos treinta días. <strong>Si vencen más de los
 * que se completan, la práctica no engancha</strong>, y eso tiene que verse en una cifra.
 */
@Service
public class PracticeMetrics {

    static final int DIAS = 30;

    private final JdbcTemplate jdbc;
    private final PracticeAiBudget presupuesto;
    private final PlatformSettingsService settings;
    private final Clock clock;

    public PracticeMetrics(JdbcTemplate jdbc, PracticeAiBudget presupuesto, PlatformSettingsService settings,
                           Clock clock) {
        this.jdbc = jdbc;
        this.presupuesto = presupuesto;
        this.settings = settings;
        this.clock = clock;
    }

    /** @param generados los que llegaron a ofrecerse (listos, empezados, completados o vencidos) */
    public record Panel(long generados, long completados, long vencidos, long fallidos, long pendientes,
                        long gastadoHoyCop, long topeCop, boolean encendida) {
    }

    @Transactional(readOnly = true)
    public Panel panel() {
        Timestamp desde = Timestamp.from(clock.instant().minus(Duration.ofDays(DIAS)));
        return new Panel(
                contar("status in ('READY', 'IN_PROGRESS', 'COMPLETED', 'EXPIRED')", desde),
                contar("status = 'COMPLETED'", desde),
                contar("status = 'EXPIRED'", desde),
                contar("status = 'FAILED'", desde),
                contar("status = 'PENDING'", desde),
                presupuesto.gastadoHoy(), settings.getInt("practice_daily_budget_cop"),
                settings.getBoolean("practice_enabled"));
    }

    private long contar(String condicion, Timestamp desde) {
        Long n = jdbc.queryForObject("select count(*) from practice_sets where " + condicion + " and created_at >= ?",
                Long.class, desde);
        return n == null ? 0 : n;
    }
}
