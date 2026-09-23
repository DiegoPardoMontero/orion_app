package co.orion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El limitador es una clase pura: sin Spring, sin reloj del sistema, con el «ahora» por parámetro.
 * Eso es lo que permite comprobar que una ventana de quince minutos se vacía sin esperar quince
 * minutos, que es la única forma de que estas reglas estén realmente probadas.
 */
class RateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-09-08T15:00:00Z");
    private static final Duration VENTANA = Duration.ofMinutes(15);

    @Test
    @DisplayName("Deja pasar hasta el límite y corta el siguiente")
    void dejaPasarHastaElLimite() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("k", 5, VENTANA, T0)).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 5, VENTANA, T0)).isFalse();
    }

    @Test
    @DisplayName("La ventana se desliza: al salir el más viejo, entra uno nuevo")
    void laVentanaSeDesliza() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("k", 5, VENTANA, T0);
        }

        // Un segundo antes de que caduque el primero: sigue lleno.
        assertThat(limiter.tryAcquire("k", 5, VENTANA, T0.plus(VENTANA).minusSeconds(1))).isFalse();
        // Justo después: el más viejo salió y hay hueco para uno.
        assertThat(limiter.tryAcquire("k", 5, VENTANA, T0.plus(VENTANA).plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("Las claves no se estorban entre sí")
    void lasClavesSonIndependientes() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("ana", 5, VENTANA, T0);
        }

        assertThat(limiter.tryAcquire("ana", 5, VENTANA, T0)).isFalse();
        assertThat(limiter.tryAcquire("carlos", 5, VENTANA, T0)).isTrue();
    }

    @Test
    @DisplayName("retryAfter dice cuánto falta para el primer hueco")
    void retryAfterDiceCuantoFalta() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire("k", 1, VENTANA, T0);

        Duration falta = limiter.retryAfter("k", VENTANA, T0.plusSeconds(60));

        assertThat(falta).isEqualTo(Duration.ofMinutes(14));
    }

    @Test
    @DisplayName("retryAfter no se va a negativo cuando la ventana ya pasó")
    void retryAfterNuncaEsNegativo() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire("k", 1, VENTANA, T0);

        assertThat(limiter.retryAfter("k", VENTANA, T0.plusSeconds(3600))).isEqualTo(Duration.ZERO);
    }

    /** Un login correcto olvida los fallos: si no, acertar la contraseña no serviría de nada. */
    @Test
    @DisplayName("reset olvida los intentos de una clave")
    void resetOlvidaLosIntentos() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("k", 5, VENTANA, T0);
        }
        assertThat(limiter.tryAcquire("k", 5, VENTANA, T0)).isFalse();

        limiter.reset("k");

        assertThat(limiter.tryAcquire("k", 5, VENTANA, T0)).isTrue();
    }

    /**
     * El limitador no puede ser la fuga de memoria que tumbe el servidor: quien varíe el correo en
     * cada intento no debe hacer crecer el mapa sin fin. Al llenarse purga lo vencido y, si aun así
     * no cabe, deja pasar — negarle el paso a todo el mundo por estar lleno sería una denegación
     * de servicio hecha por nosotros mismos.
     */
    @Test
    @DisplayName("Con muchísimas claves distintas no crece sin límite ni cierra la puerta")
    void noCreceSinLimite() {
        RateLimiter limiter = new RateLimiter();

        // Todas vencidas: la purga debe poder recuperarlas todas.
        for (int i = 0; i < 101_000; i++) {
            limiter.tryAcquire("vieja-" + i, 5, VENTANA, T0);
        }

        // Mucho después, una clave nueva sigue pasando.
        assertThat(limiter.tryAcquire("nueva", 5, VENTANA, T0.plusSeconds(3600))).isTrue();
    }

    @Test
    @DisplayName("Llenarlo de claves inventadas no apaga el freno de una clave que ya está cortada")
    void llenoNoAbreLaPuerta() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("login:atacante", 5, VENTANA, T0);
        }
        assertThat(limiter.tryAcquire("login:atacante", 5, VENTANA, T0)).isFalse();

        // Cien mil claves nuevas en la misma ventana, ninguna vencida: antes esto dejaba pasar todo.
        for (int i = 0; i < 100_000; i++) {
            limiter.tryAcquire("relleno-" + i, 5, VENTANA, T0.plusMillis(i + 1));
        }

        assertThat(limiter.tryAcquire("login:atacante", 5, VENTANA, T0.plusSeconds(200))).isFalse();
    }

    @Test
    @DisplayName("Purgar respeta la ventana de cada clave: un límite diario no se borra a los quince minutos")
    void cadaClaveSuVentana() {
        RateLimiter limiter = new RateLimiter();
        Duration dia = Duration.ofDays(1);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("diario", 3, dia, T0);
        }
        // Una hora después, el mapa se llena y se purga con claves de 15 minutos.
        for (int i = 0; i < 100_000; i++) {
            limiter.tryAcquire("corta-" + i, 5, VENTANA, T0.plusSeconds(3600));
        }

        assertThat(limiter.tryAcquire("diario", 3, dia, T0.plusSeconds(3601))).isFalse();
    }
}
