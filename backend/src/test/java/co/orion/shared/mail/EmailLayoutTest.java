package co.orion.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * La plantilla es una clase pura: sin Spring, sin red. Lo que se fija aquí es lo que un cambio
 * descuidado rompería sin que nadie lo notara hasta ver un correo feo en producción.
 */
class EmailLayoutTest {

    private final EmailLayout layout = new EmailLayout("https://orion.co");

    @Test
    void elLogoApuntaAlOrigenPublicoConfigurado() {
        String html = layout.wrap("<p>Hola.</p>");

        assertThat(html).contains("src=\"https://orion.co/email/orion-logo.png\"");
    }

    @Test
    void unaBarraFinalEnLaBaseNoDuplicaLaDeLaRuta() {
        EmailLayout conBarra = new EmailLayout("https://orion.co/");

        assertThat(conBarra.wrap("<p>Hola.</p>")).contains("https://orion.co/email/orion-logo.png");
    }

    @Test
    void elLogoLlevaAltYMedidasExplicitas() {
        String html = layout.wrap("<p>Hola.</p>");

        // El alt es lo único que ve quien bloquea las imágenes remotas.
        assertThat(html).contains("alt=\"Orión\"");
        // Outlook de escritorio ignora el CSS y usa los atributos: sin ellos pinta el PNG a 360 px
        // y desborda la tarjeta.
        assertThat(html).contains("width=\"180\"").contains("height=\"62\"");
    }

    @Test
    void elCuerpoViajaIntacto() {
        String cuerpo = "<p>Tu clase quedó confirmada.</p><ul><li>mié 15 jul</li></ul>";

        assertThat(layout.wrap(cuerpo)).contains(cuerpo);
    }

    @Test
    void elPieLlevaElEsloganVigente() {
        assertThat(layout.wrap("<p>Hola.</p>")).contains("Find the right teacher, learn your way.");
    }

    @Test
    void esUnDocumentoCompletoYNoUnFragmento() {
        String html = layout.wrap("<p>Hola.</p>");

        assertThat(html).startsWith("<!DOCTYPE html").endsWith("</html>\n");
    }
}
