package co.orion.shared.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vigila que los procesos programados sigan corriendo.
 *
 * <p>Es la alerta que de verdad importa. Un job que no corre es indistinguible de uno que corre y
 * no encuentra nada que hacer, y el de cierre de clases es el que libera los pagos: si se detiene,
 * el síntoma tarda semanas y llega como «no me han pagado», con un profesor enfadado y sin forma
 * de saber desde cuándo.
 *
 * <p>Avisa cuando se cae <strong>y también cuando vuelve</strong>. Saber que se arregló importa
 * tanto como saber que se rompió: sin el segundo aviso, la única forma de enterarse de que ya
 * está bien es entrar a mirar, que es justo lo que estas alertas existen para evitar.
 */
@Component
public class JobWatchdog {

    private static final Logger log = LoggerFactory.getLogger(JobWatchdog.class);

    /**
     * Cuánto puede tardar cada proceso antes de que se considere caído. El margen es generoso a
     * propósito: una alerta que salta por un retraso de dos minutos enseña a ignorar las alertas.
     */
    private static final List<Vigilado> VIGILADOS = List.of(
            new Vigilado("lesson-auto-complete", "Cierre de clases", Duration.ofHours(3),
                    "Es el que libera los pagos. Si sigue detenido, nadie cobra."),
            new Vigilado("payment-expiry", "Expiración de reservas", Duration.ofMinutes(30),
                    "Los cupos sin pagar se quedan bloqueados y nadie más puede reservarlos."),
            new Vigilado("professor-metrics", "Métricas y ranking", Duration.ofHours(30),
                    "El orden del buscador se queda congelado en el de ayer."),
            new Vigilado("class-reminders", "Recordatorios de clase", Duration.ofMinutes(30),
                    "Nadie recibe el aviso de su clase de mañana ni el de una hora antes."));

    private final JobRunRegistry runs;
    private final AlertService alerts;
    private final Clock clock;

    /**
     * Cuándo arrancó esta instancia. El registro de corridas vive en memoria y se pierde al
     * reiniciar; sin esta marca, cada despliegue dispararía tres alertas de procesos «caídos» que
     * en realidad no han tenido tiempo de correr todavía.
     */
    private final Instant arranque;

    /** Qué procesos están dados por caídos ahora mismo, para avisar una vez y no en cada ronda. */
    private final Map<String, Boolean> caidos = new ConcurrentHashMap<>();

    public JobWatchdog(JobRunRegistry runs, AlertService alerts, Clock clock) {
        this.runs = runs;
        this.alerts = alerts;
        this.clock = clock;
        this.arranque = clock.instant();
    }

    @Scheduled(fixedDelayString = "${orion.alerts.watchdog.interval-ms:900000}",
            initialDelayString = "${orion.alerts.watchdog.initial-delay-ms:900000}")
    public void revisar() {
        Instant now = clock.instant();
        Map<String, JobRunRegistry.JobRun> ultimas = runs.all().stream()
                .collect(java.util.stream.Collectors.toMap(
                        JobRunRegistry.JobRun::job, r -> r, (a, b) -> b));

        for (Vigilado vigilado : VIGILADOS) {
            Optional<JobRunRegistry.JobRun> ultima =
                    Optional.ofNullable(ultimas.get(vigilado.job()));

            // La referencia es la última corrida o, si nunca ha corrido en esta instancia, el
            // arranque. Así un despliegue no dispara alertas por procesos que aún no tocaba correr.
            Instant referencia = ultima.map(JobRunRegistry.JobRun::at).orElse(arranque);
            boolean tarda = referencia.plus(vigilado.tolerancia()).isBefore(now);
            boolean falloUltima = ultima.map(r -> !r.ok()).orElse(false);
            boolean malo = tarda || falloUltima;

            boolean estabaCaido = caidos.getOrDefault(vigilado.job(), false);
            if (malo && !estabaCaido) {
                caidos.put(vigilado.job(), true);
                alertarCaida(vigilado, ultima, referencia, now, falloUltima);
            } else if (!malo && estabaCaido) {
                caidos.put(vigilado.job(), false);
                alerts.alert("job-ok:" + vigilado.job(),
                        vigilado.nombre() + " volvió a correr",
                        "El proceso «" + vigilado.nombre() + "» está corriendo otra vez.\n"
                                + "Última corrida: " + referencia + ".");
            }
        }
    }

    private void alertarCaida(Vigilado vigilado, Optional<JobRunRegistry.JobRun> ultima,
                              Instant referencia, Instant now, boolean falloUltima) {
        String motivo = falloUltima
                ? "Su última corrida terminó con error: " + ultima.map(JobRunRegistry.JobRun::detail)
                        .orElse("sin detalle")
                : "No corre desde hace " + Duration.between(referencia, now).toMinutes()
                        + " minutos (se espera al menos cada " + vigilado.tolerancia().toMinutes()
                        + ").";

        log.error("Proceso {} en mal estado: {}", vigilado.job(), motivo);
        alerts.alert("job-caido:" + vigilado.job(),
                vigilado.nombre() + " no está corriendo",
                "Proceso: " + vigilado.nombre() + " (" + vigilado.job() + ")\n"
                        + motivo + "\n\n"
                        + vigilado.consecuencia() + "\n\n"
                        + (ultima.isEmpty()
                                ? "No ha corrido ninguna vez desde que arrancó la aplicación."
                                : "Última corrida conocida: " + referencia + "."));
    }

    private record Vigilado(String job, String nombre, Duration tolerancia, String consecuencia) {
    }
}
