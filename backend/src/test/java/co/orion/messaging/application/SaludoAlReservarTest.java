package co.orion.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El texto del saludo: no genérico, con su estrella, distinto la primera vez. */
class SaludoAlReservarTest {

    @Test
    @DisplayName("La primera clase juntos se presenta y, si hay objetivo en la ficha, lo menciona")
    void primeraClase() {
        String t = SaludoAlReservar.texto("Ana", "María", "jueves 26 de septiembre", "7:00 PM", true, "tus viajes");

        assertThat(t).startsWith("¡Hola, Ana! ⭐ Soy María, tu profe en Orión.")
                .contains("nuestra primera clase: el jueves 26 de septiembre a las 7:00 PM (hora de Colombia)")
                .contains("Vi en tu ficha que lo quieres para tus viajes");
    }

    @Test
    @DisplayName("Sin objetivo en la ficha no se inventa uno")
    void sinObjetivo() {
        assertThat(SaludoAlReservar.texto("Ana", "María", "jueves 26 de septiembre", "7:00 PM", true, null))
                .doesNotContain("ficha");
    }

    @Test
    @DisplayName("Las siguientes saludan de nuevo, sin volver a presentarse")
    void deNuevo() {
        String t = SaludoAlReservar.texto("Ana", "María", "lunes 5 de octubre", "8:00 AM", false, "tus viajes");

        assertThat(t).startsWith("¡Hola de nuevo, Ana! ⭐ Soy María.").contains("próxima clase")
                .doesNotContain("tu profe en Orión");
    }
}
