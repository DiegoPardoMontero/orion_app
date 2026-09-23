package co.orion.practice.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.observability.AlertService;
import co.orion.shared.time.BusinessZone;

/**
 * El tope de gasto de la práctica, aparte del acta y del diagnóstico: uno por función (decisión de
 * Pardo, 22/09/2026). Sin presupuesto, los sets se quedan pendientes hasta el día siguiente: no se
 * marcan fallidos por algo que no es culpa del acta.
 */
@Component
public class PracticeAiBudget {

    static final String FEATURE = "practice";

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final AlertService alertas;
    private final double entradaPorMillon;
    private final double salidaPorMillon;
    private final long pesosPorDolar;
    private final TransactionTemplate enSuPropiaTransaccion;
    private final Clock clock;

    public PracticeAiBudget(JdbcTemplate jdbc, PlatformSettingsService settings, AlertService alertas,
                            @Value("${orion.ai.text-input-usd-per-million:0.25}") double entradaPorMillon,
                            @Value("${orion.ai.text-output-usd-per-million:2.0}") double salidaPorMillon,
                            @Value("${orion.ai.usd-to-cop:3101}") long pesosPorDolar,
                            PlatformTransactionManager transacciones, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.alertas = alertas;
        this.entradaPorMillon = entradaPorMillon;
        this.salidaPorMillon = salidaPorMillon;
        this.pesosPorDolar = pesosPorDolar;
        this.enSuPropiaTransaccion = new TransactionTemplate(transacciones);
        this.enSuPropiaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public boolean disponible() {
        return gastadoHoy() < settings.getInt("practice_daily_budget_cop");
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

    /**
     * En su propia transacción, como {@code AiUsageRecorder}: la llamada se hizo y se pagó aunque la
     * generación del set termine en rollback, y un gasto que no queda escrito es un tope que no frena.
     */
    public void registrar(UUID actor, String modelo, Integer entrada, Integer salida, int latenciaMs,
                          String resultado) {
        enSuPropiaTransaccion.executeWithoutResult(estado ->
                escribir(actor, modelo, entrada, salida, latenciaMs, resultado));
    }

    private void escribir(UUID actor, String modelo, Integer entrada, Integer salida, int latenciaMs,
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
        int tope = settings.getInt("practice_daily_budget_cop");
        if (tope > 0 && gastadoHoy() * 100 >= tope * 80L) {
            alertas.alert("practica-presupuesto-80", "La práctica va por el 80 % del presupuesto de IA de hoy",
                    "Gastado hoy: " + gastadoHoy() + " COP de " + tope + " COP. Al llegar al tope, los sets "
                            + "nuevos esperan a mañana. El tope se cambia en Administración → Ajustes "
                            + "(practice_daily_budget_cop).");
        }
    }
}
