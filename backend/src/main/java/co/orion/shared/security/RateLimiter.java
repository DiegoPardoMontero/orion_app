package co.orion.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante en memoria: cuántos intentos lleva una clave en los últimos N minutos.
 *
 * <p><strong>Por qué en memoria y no en Redis.</strong> Orión corre en una instancia y atiende a
 * decenas de personas. Un contador distribuido sería arquitectura para un problema que no tenemos,
 * y traería una dependencia que puede caerse — y cuando el limitador se cae hay que decidir si se
 * abre o se cierra, que es una decisión que preferimos no tener que tomar. Si algún día hay dos
 * instancias, esto se sustituye; el contrato de {@link #tryAcquire} no cambia.
 *
 * <p>El «ahora» entra por parámetro, como en {@code SlotCalculator}: así el test no espera quince
 * minutos para comprobar que la ventana se vacía.
 */
public class RateLimiter {

    /**
     * Cota de claves distintas. Sin ella, un atacante que varíe el correo en cada intento haría
     * crecer el mapa sin límite: el limitador se convertiría en la fuga de memoria que lo tumbe.
     * Al llegar al tope se purgan las entradas ya vencidas; si aun así no cabe, se deja pasar —
     * cerrar la puerta a todo el mundo por estar lleno sería una denegación de servicio hecha por
     * nosotros mismos.
     */
    private static final int MAX_CLAVES = 10_000;

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();

    /**
     * Registra un intento y dice si cabe dentro del límite.
     *
     * @return {@code true} si el intento se permite; {@code false} si excede el límite.
     */
    public boolean tryAcquire(String key, int limit, Duration window, Instant now) {
        Instant desde = now.minus(window);

        if (intentos.size() >= MAX_CLAVES && !intentos.containsKey(key)) {
            purgar(now, window);
            if (intentos.size() >= MAX_CLAVES) {
                return true;
            }
        }

        Deque<Instant> marcas = intentos.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && !marcas.peekFirst().isAfter(desde)) {
                marcas.pollFirst();
            }
            if (marcas.size() >= limit) {
                return false;
            }
            marcas.addLast(now);
            return true;
        }
    }

    /** Cuánto falta para que se libere un hueco. Es lo que va en la cabecera {@code Retry-After}. */
    public Duration retryAfter(String key, Duration window, Instant now) {
        Deque<Instant> marcas = intentos.get(key);
        if (marcas == null) {
            return Duration.ZERO;
        }
        synchronized (marcas) {
            Instant masAntiguo = marcas.peekFirst();
            if (masAntiguo == null) {
                return Duration.ZERO;
            }
            Duration falta = Duration.between(now, masAntiguo.plus(window));
            return falta.isNegative() ? Duration.ZERO : falta;
        }
    }

    /** Olvida los intentos de una clave. Se llama tras un login correcto. */
    public void reset(String key) {
        intentos.remove(key);
    }

    /**
     * Olvida todo. Lo usan los tests de integración entre casos: comparten el contexto de Spring y
     * por tanto este bean, y varios congelan el reloj — con el «ahora» quieto la ventana nunca se
     * desliza y el vigesimoprimer login de la suite chocaría con un límite pensado para un humano.
     */
    public void resetAll() {
        intentos.clear();
    }

    private void purgar(Instant now, Duration window) {
        Instant desde = now.minus(window);
        intentos.entrySet().removeIf(entry -> {
            Deque<Instant> marcas = entry.getValue();
            synchronized (marcas) {
                return marcas.isEmpty() || !marcas.peekLast().isAfter(desde);
            }
        });
    }
}
