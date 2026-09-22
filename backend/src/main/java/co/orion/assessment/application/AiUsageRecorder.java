package co.orion.assessment.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.assessment.domain.AiUsageLog;
import co.orion.assessment.domain.AiUsageOutcome;
import co.orion.assessment.persistence.AiUsageLogRepository;

/**
 * Anota lo que gasta la IA.
 *
 * <p><strong>El costo es una estimación, y conviene saberlo.</strong> Como el audio va directo del
 * navegador al proveedor, Orión no ve los tokens que se consumieron: solo sabe cuánto duró la
 * conversación. Así que el gasto se calcula por minuto a un precio configurado. Sirve para lo que
 * tiene que servir —frenar la función antes de que se desboque— pero la cifra buena es la del
 * panel del proveedor, y las dos no van a cuadrar al peso.
 *
 * <p>Escribe en su propia transacción: que falle el registro del gasto no puede tumbar un
 * diagnóstico que la persona ya terminó, y al revés, un diagnóstico que hizo rollback igual
 * consumió minutos que hay que contar.
 *
 * <p>Por eso usa {@code TransactionTemplate} y no {@code @Transactional}. Con la anotación, un
 * try/catch dentro del mismo método <strong>no protege</strong>: el fallo ya marcó la transacción
 * como rollback-only y el error reaparece al confirmarla, con un mensaje que no dice nada de lo que
 * pasó. Aquí el catch está fuera de la transacción, que es el único sitio donde sirve.
 */
@Service
public class AiUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);

    public static final String FEATURE_DIAGNOSTICO = "confidence_assessment";

    private final AiUsageLogRepository logs;
    private final TransactionTemplate enSuPropiaTransaccion;
    private final long milesimasDeDolarPorMinuto;
    private final double dolaresPorMillonDeEntrada;
    private final double dolaresPorMillonDeSalida;
    private final long pesosPorDolar;
    private final Clock clock;

    public AiUsageRecorder(
            AiUsageLogRepository logs,
            PlatformTransactionManager transacciones,
            // En milésimas de dólar para no meter decimales: 16 = 0,016 USD por minuto, que es lo
            // que cuesta hoy el modelo mini. Cambiar de modelo es cambiar este número.
            @Value("${orion.ai.voice-cost-per-minute-millis-usd:16}") long milesimasDeDolarPorMinuto,
            // Precio del modelo de texto por millón de tokens: los de gpt-5-mini a septiembre de 2026.
            @Value("${orion.ai.text-input-usd-per-million:0.25}") double dolaresPorMillonDeEntrada,
            @Value("${orion.ai.text-output-usd-per-million:2.0}") double dolaresPorMillonDeSalida,
            @Value("${orion.ai.usd-to-cop:3101}") long pesosPorDolar,
            Clock clock) {
        this.logs = logs;
        this.enSuPropiaTransaccion = new TransactionTemplate(transacciones);
        this.enSuPropiaTransaccion.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.milesimasDeDolarPorMinuto = milesimasDeDolarPorMinuto;
        this.dolaresPorMillonDeEntrada = dolaresPorMillonDeEntrada;
        this.dolaresPorMillonDeSalida = dolaresPorMillonDeSalida;
        this.pesosPorDolar = pesosPorDolar;
        this.clock = clock;
    }

    public void conversacion(UUID actorId, String provider, String model,
                             Duration duracion, AiUsageOutcome outcome) {
        int segundos = (int) Math.max(0, duracion.getSeconds());
        guardar(AiUsageLog.voice(FEATURE_DIAGNOSTICO, actorId, provider, model,
                segundos, costoEnPesos(segundos), outcome), actorId);
    }

    /** El proveedor falló al abrir: no hubo conversación, pero sí hubo intento que contar. */
    public void intentoFallido(UUID actorId, String provider, String model,
                               Duration tardanza, AiUsageOutcome outcome) {
        guardar(AiUsageLog.failedAttempt(FEATURE_DIAGNOSTICO, actorId, provider, model,
                (int) tardanza.toMillis(), outcome), actorId);
    }

    /**
     * Una llamada de texto del diagnóstico —el resumen—. Se carga al mismo presupuesto que la
     * conversación: es parte de la misma función, y el tope es uno por función.
     */
    public void texto(UUID actorId, String provider, String model, Integer entrada, Integer salida,
                      int latenciaMs, AiUsageOutcome outcome) {
        double dolares = ((entrada == null ? 0 : entrada) * dolaresPorMillonDeEntrada
                + (salida == null ? 0 : salida) * dolaresPorMillonDeSalida) / 1_000_000.0;
        long pesos = (long) Math.ceil(dolares * pesosPorDolar);
        guardar(AiUsageLog.text(FEATURE_DIAGNOSTICO, actorId, provider, model, entrada, salida,
                pesos, latenciaMs, outcome), actorId);
    }

    private void guardar(AiUsageLog fila, UUID actorId) {
        try {
            enSuPropiaTransaccion.executeWithoutResult(estado -> logs.save(fila));
        } catch (RuntimeException ex) {
            log.error("No se pudo registrar el consumo de IA de {}", actorId, ex);
        }
    }

    /** Redondeo hacia arriba: para un tope de gasto, quedarse corto es el error caro. */
    private long costoEnPesos(int segundos) {
        long milesimas = (long) Math.ceil(segundos * milesimasDeDolarPorMinuto / 60.0);
        return (long) Math.ceil(milesimas * pesosPorDolar / 1000.0);
    }

    Clock clock() {
        return clock;
    }
}
