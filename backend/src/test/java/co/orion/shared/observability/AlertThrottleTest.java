package co.orion.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.shared.time.BusinessZone;

/**
 * El freno de las alertas.
 *
 * <p>Sin él, un error en bucle manda seiscientos correos y a la semana siguiente nadie los abre —
 * y una bandeja que nadie abre es exactamente igual de ciega que no tener alertas. Estas pruebas
 * son las que garantizan que el aviso siga significando algo cuando llegue.
 */
class AlertThrottleTest {

    private static final Instant T0 = Instant.parse("2026-09-08T15:00:00Z");

    private static AlertThrottle nuevo(int minutos, int tope) {
        return new AlertThrottle(Duration.ofMinutes(minutos), tope, BusinessZone.BOGOTA);
    }

    @Test
    @DisplayName("La primera de una firma pasa; la segunda dentro de la ventana, no")
    void unaPorFirmaYVentana() {
        AlertThrottle throttle = nuevo(60, 50);

        assertThat(throttle.deberiaEnviar("A", T0)).isTrue();
        assertThat(throttle.deberiaEnviar("A", T0.plusSeconds(1))).isFalse();
        assertThat(throttle.deberiaEnviar("A", T0.plusSeconds(59 * 60))).isFalse();
    }

    @Test
    @DisplayName("Pasada la ventana, la misma firma vuelve a pasar")
    void pasadaLaVentanaVuelveAPasar() {
        AlertThrottle throttle = nuevo(60, 50);
        throttle.deberiaEnviar("A", T0);

        assertThat(throttle.deberiaEnviar("A", T0.plusSeconds(60 * 60 + 1))).isTrue();
    }

    /** Firmas distintas son fallos distintos: agruparlas escondería el segundo. */
    @Test
    @DisplayName("Firmas distintas no se estorban")
    void firmasDistintas() {
        AlertThrottle throttle = nuevo(60, 50);

        assertThat(throttle.deberiaEnviar("A", T0)).isTrue();
        assertThat(throttle.deberiaEnviar("B", T0)).isTrue();
    }

    /**
     * Veinte fallos distintos a la vez son un incendio, no veinte avisos. Llegado el tope, lo que
     * hay que hacer es mirar los logs, no seguir leyendo correo.
     */
    @Test
    @DisplayName("El tope diario corta aunque las firmas sean distintas")
    void elTopeDiarioCorta() {
        AlertThrottle throttle = nuevo(60, 3);

        assertThat(throttle.deberiaEnviar("A", T0)).isTrue();
        assertThat(throttle.deberiaEnviar("B", T0)).isTrue();
        assertThat(throttle.deberiaEnviar("C", T0)).isTrue();
        assertThat(throttle.deberiaEnviar("D", T0)).isFalse();
    }

    @Test
    @DisplayName("El contador diario se reinicia al cambiar el día en Bogotá")
    void elContadorSeReinicia() {
        AlertThrottle throttle = nuevo(60, 1);
        throttle.deberiaEnviar("A", T0);
        assertThat(throttle.deberiaEnviar("B", T0)).isFalse();

        // T0 es el 8 de septiembre a las 10:00 en Bogotá; +20 h ya es el día 9.
        assertThat(throttle.deberiaEnviar("B", T0.plus(Duration.ofHours(20)))).isTrue();
    }

    /** El día se corta en Bogotá, como todo lo demás: en UTC, T0+14h ya sería otro día. */
    @Test
    @DisplayName("El corte del día es el de Bogotá, no el de UTC")
    void elDiaSeCortaEnBogota() {
        AlertThrottle throttle = nuevo(60, 1);
        throttle.deberiaEnviar("A", T0);

        // T0 + 10 h = 01:00 UTC del día 9, pero todavía las 20:00 del día 8 en Bogotá.
        assertThat(throttle.deberiaEnviar("B", T0.plus(Duration.ofHours(10)))).isFalse();
    }

    @Test
    @DisplayName("Informa de cuántas van hoy, para que el correo lo pueda decir")
    void informaDeCuantasVan() {
        AlertThrottle throttle = nuevo(60, 50);
        throttle.deberiaEnviar("A", T0);
        throttle.deberiaEnviar("B", T0);

        assertThat(throttle.enviadasHoy(T0)).isEqualTo(2);
        assertThat(throttle.topeDiario()).isEqualTo(50);
    }
}
