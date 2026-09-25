package co.orion.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El texto del saludo: no genérico, con su estrella, distinto la primera vez. */
class SaludoAlReservarTest {

    @Test
    @DisplayName("La primera clase juntos se presenta y, si hay objetivo en la ficha, lo menciona")
    void primeraClase() {
        String t = SaludoAlReservar.texto("Ana", "María", "jueves 26 de septiembre", "de 7:00 a 7:55 PM",
                "55 minutos", true, "tus viajes");

        // Con su franja y su duración: la hora de inicio sola parecía una clase de media hora.
        assertThat(t).startsWith("¡Hola, Ana! ⭐ Soy María, tu profe en Orión.")
                .contains("nuestra primera clase: el jueves 26 de septiembre, de 7:00 a 7:55 PM (hora de Colombia)."
                        + " Son 55 minutos.")
                .contains("Vi en tu ficha que lo quieres para tus viajes");
    }

    @Test
    @DisplayName("Sin objetivo en la ficha no se inventa uno")
    void sinObjetivo() {
        assertThat(SaludoAlReservar.texto("Ana", "María", "jueves 26 de septiembre", "de 7:00 a 7:55 PM",
                "55 minutos", true, null))
                .doesNotContain("ficha");
    }

    @Test
    @DisplayName("Las siguientes saludan de nuevo, sin volver a presentarse")
    void deNuevo() {
        String t = SaludoAlReservar.texto("Ana", "María", "lunes 5 de octubre", "de 8:00 a 8:55 AM", "55 minutos",
                false, "tus viajes");

        assertThat(t).startsWith("¡Hola de nuevo, Ana! ⭐ Soy María.")
                .contains("próxima clase: el lunes 5 de octubre, de 8:00 a 8:55 AM (hora de Colombia). Son 55 minutos.")
                .doesNotContain("tu profe en Orión");
    }

    @Test
    @DisplayName("La de prueba también dice su franja y cuánto dura")
    void dePrueba() {
        assertThat(SaludoAlReservar.textoDePrueba("Ana", "María", "jueves 26 de septiembre", "de 7:00 a 7:55 PM",
                "55 minutos", null))
                .contains("nuestra clase de prueba: el jueves 26 de septiembre, de 7:00 a 7:55 PM (hora de Colombia),"
                        + " 55 minutos.");
    }
}
