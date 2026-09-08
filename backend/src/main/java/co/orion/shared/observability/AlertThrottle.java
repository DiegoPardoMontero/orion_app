package co.orion.shared.observability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decide si una alerta se manda o se calla.
 *
 * <p>Sin freno, esto no sirve para nada. Un error en bucle manda seiscientos correos, la bandeja se
 * satura y a la semana siguiente nadie los abre — y una bandeja que nadie abre es exactamente igual
 * de ciega que no tener alertas. El freno no es una optimización: es lo que hace que el aviso
 * signifique algo cuando llega.
 *
 * <p>Dos límites, y los dos hacen falta. Uno <strong>por firma</strong>, para que un mismo fallo
 * repetido sea un correo y no una avalancha. Y uno <strong>global diario</strong>, porque veinte
 * fallos distintos a la vez son un incendio, no veinte avisos: llegado el tope, lo que hay que
 * hacer es mirar los logs, no leer más correo.
 *
 * <p>Clase pura, con el «ahora» por parámetro. Comprobar que una ventana de una hora se abre no
 * debería costar una hora.
 */
public class AlertThrottle {

    private final Duration ventanaPorFirma;
    private final int topeDiario;
    private final ZoneId zona;

    private final Map<String, Instant> ultimoEnvio = new ConcurrentHashMap<>();
    private final AtomicInteger enviadosHoy = new AtomicInteger();
    private volatile LocalDate diaDelContador;

    public AlertThrottle(Duration ventanaPorFirma, int topeDiario, ZoneId zona) {
        this.ventanaPorFirma = ventanaPorFirma;
        this.topeDiario = topeDiario;
        this.zona = zona;
    }

    /**
     * Registra un intento de alerta y dice si toca mandarla.
     *
     * @return {@code true} si hay que enviar; {@code false} si se calla por el freno.
     */
    public synchronized boolean deberiaEnviar(String firma, Instant now) {
        rotarContadorSiCambioElDia(now);

        if (enviadosHoy.get() >= topeDiario) {
            return false;
        }

        Instant ultimo = ultimoEnvio.get(firma);
        if (ultimo != null && ultimo.plus(ventanaPorFirma).isAfter(now)) {
            return false;
        }

        ultimoEnvio.put(firma, now);
        enviadosHoy.incrementAndGet();
        return true;
    }

    /** Cuántas alertas van hoy. Va dentro del correo: saber que es la número 40 de 50 informa. */
    public int enviadasHoy(Instant now) {
        rotarContadorSiCambioElDia(now);
        return enviadosHoy.get();
    }

    public int topeDiario() {
        return topeDiario;
    }

    /** El día se corta en Bogotá, como todo lo demás en Orión. */
    private synchronized void rotarContadorSiCambioElDia(Instant now) {
        LocalDate hoy = LocalDate.ofInstant(now, zona);
        if (!hoy.equals(diaDelContador)) {
            diaDelContador = hoy;
            enviadosHoy.set(0);
        }
    }
}
