package co.orion.teaching.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.observability.AlertService;
import co.orion.shared.time.BusinessZone;

/**
 * El gasto de IA de las actas: su propio tope diario (Pardo, 22/09/2026: un tope por función, para
 * que el diagnóstico no deje sin borradores a los profesores ni al revés), su registro en
 * {@code ai_usage_log} con {@code feature = 'lesson_note'} y el aviso al 80 %.
 *
 * <p>Escribe con SQL directo sobre la tabla que creó el diagnóstico en vez de importar su entidad:
 * así {@code teaching} no depende de {@code assessment}, que es un módulo del que no depende nadie.
 */
@Component
public class TeachingAiBudget {

    static final String FEATURE = "lesson_note";

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final AlertService alertas;
    private final double entradaPorMillon;
    private final double salidaPorMillon;
    private final long pesosPorDolar;
    private final Clock clock;

    public TeachingAiBudget(JdbcTemplate jdbc, PlatformSettingsService settings, AlertService alertas,
                            @Value("${orion.ai.text-input-usd-per-million:0.25}") double entradaPorMillon,
                            @Value("${orion.ai.text-output-usd-per-million:2.0}") double salidaPorMillon,
                            @Value("${orion.ai.usd-to-cop:3101}") long pesosPorDolar,
                            Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.alertas = alertas;
        this.entradaPorMillon = entradaPorMillon;
        this.salidaPorMillon = salidaPorMillon;
        this.pesosPorDolar = pesosPorDolar;
        this.clock = clock;
    }

    /** Si hoy todavía se puede llamar a la IA: encendida y por debajo del tope. */
    public boolean disponible() {
        return settings.getBoolean("ai_lesson_notes_enabled")
                && gastadoHoy() < settings.getInt("ai_daily_budget_cop");
    }

    public long gastadoHoy() {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
        Instant desde = hoy.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Long total = jdbc.queryForObject("""
                select coalesce(sum(cost_cop), 0) from ai_usage_log
                where feature = ? and occurred_at >= ? and occurred_at < ?
                """, Long.class, FEATURE, Timestamp.from(desde),
                Timestamp.from(hoy.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant()));
        return total == null ? 0 : total;
    }

    /** Toda llamada deja su fila, termine como termine (brief, paso A2). */
    public void registrar(UUID actor, String modelo, Integer entrada, Integer salida, int latenciaMs,
                          String resultado) {
        double dolares = ((entrada == null ? 0 : entrada) * entradaPorMillon
                + (salida == null ? 0 : salida) * salidaPorMillon) / 1_000_000.0;
        long pesos = (long) Math.ceil(dolares * pesosPorDolar);
        jdbc.update("""
                insert into ai_usage_log (feature, actor_id, provider, model, input_tokens, output_tokens,
                                          cost_cop, latency_ms, outcome, occurred_at)
                values (?, ?, 'openai-text', ?, ?, ?, ?, ?, ?, ?)
                """, FEATURE, actor, modelo, entrada, salida, pesos, latenciaMs, resultado,
                Timestamp.from(clock.instant()));

        int tope = settings.getInt("ai_daily_budget_cop");
        if (tope > 0 && gastadoHoy() * 100 >= tope * 80L) {
            alertas.alert("actas-presupuesto-80", "Las actas van por el 80 % del presupuesto de IA de hoy",
                    "Gastado hoy: " + gastadoHoy() + " COP de " + tope + " COP. Al llegar al tope, los "
                            + "profesores escriben el acta a mano hasta mañana. El tope se cambia en "
                            + "Administración → Ajustes (ai_daily_budget_cop).");
        }
    }
}
