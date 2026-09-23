package co.orion.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
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
     *
     * <p><strong>Lleno, nunca abre la puerta.</strong> Antes, si tras purgar lo vencido seguía
     * lleno, dejaba pasar; eso convertía «llénalo con diez mil claves inventadas» en un interruptor
     * que apagaba todos los frenos (auditoría del 22/09/2026). Ahora desaloja, de a un lote, las
     * claves que llevan más tiempo quietas <em>y que no están frenando a nadie</em>: la que ya llegó
     * a su límite es justo la que el atacante querría que se olvidara, así que es la última en irse.
     * Cien mil claves son unos pocos megas.
     */
    private static final int MAX_CLAVES = 100_000;

    /** Cuántas se desalojan de una vez, para no recorrer el mapa en cada petición de una inundación. */
    private static final int LOTE = MAX_CLAVES / 100;

    /**
     * Cada clave recuerda su ventana y su límite: purgar con la ventana de otra borraba contadores
     * diarios, y sin el límite no se sabe cuáles están frenando a alguien.
     */
    private record Registro(Duration ventana, int limite, Deque<Instant> marcas) {
    }

    private final Map<String, Registro> intentos = new ConcurrentHashMap<>();

    /**
     * Registra un intento y dice si cabe dentro del límite.
     *
     * @return {@code true} si el intento se permite; {@code false} si excede el límite.
     */
    public boolean tryAcquire(String key, int limit, Duration window, Instant now) {
        Instant desde = now.minus(window);

        if (intentos.size() >= MAX_CLAVES && !intentos.containsKey(key)) {
            purgar(now);
            if (intentos.size() >= MAX_CLAVES) {
                desalojarLasMasQuietas();
            }
        }

        Registro registro = intentos.computeIfAbsent(key, k -> new Registro(window, limit, new ArrayDeque<>()));
        Deque<Instant> marcas = registro.marcas();
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
        Registro registro = intentos.get(key);
        if (registro == null) {
            return Duration.ZERO;
        }
        Deque<Instant> marcas = registro.marcas();
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

    /** Quita lo que ya no cuenta, cada clave según su propia ventana. */
    private void purgar(Instant now) {
        intentos.entrySet().removeIf(entry -> {
            Registro r = entry.getValue();
            synchronized (r.marcas()) {
                return r.marcas().isEmpty() || !r.marcas().peekLast().isAfter(now.minus(r.ventana()));
            }
        });
    }

    /** Un lote de las más quietas; primero las que no frenan a nadie, las frenadas solo si no queda otra. */
    private void desalojarLasMasQuietas() {
        record Candidata(String clave, Instant ultima, boolean frenando) {
        }
        List<Candidata> todas = new ArrayList<>(intentos.size());
        intentos.forEach((clave, r) -> {
            synchronized (r.marcas()) {
                Instant ultima = r.marcas().peekLast();
                todas.add(new Candidata(clave, ultima == null ? Instant.MIN : ultima,
                        r.marcas().size() >= r.limite()));
            }
        });
        todas.sort(Comparator.comparing(Candidata::frenando).thenComparing(Candidata::ultima));
        todas.stream().limit(LOTE).forEach(c -> intentos.remove(c.clave()));
    }
}
