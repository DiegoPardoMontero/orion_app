package co.orion.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** El decorador: que marque el HTML, que no toque nada más y que no se trague los adjuntos. */
class BrandedMailTransportTest {

    private final List<OutgoingEmail> entregados = new ArrayList<>();
    private final BrandedMailTransport transport =
            new BrandedMailTransport(entregados::add, new EmailLayout("https://orion.co"));

    @Test
    void elHtmlSaleConLaPlantillaDeMarca() {
        transport.send(OutgoingEmail.plain("ana@orion.test", "Asunto", "texto", "<p>Hola.</p>"));

        assertThat(entregados).hasSize(1);
        assertThat(entregados.getFirst().htmlBody())
                .contains("orion-logo.png")
                .contains("<p>Hola.</p>");
    }

    @Test
    void elTextoPlanoNoSeToca() {
        transport.send(OutgoingEmail.plain("ana@orion.test", "Asunto", "texto plano", "<p>Hola.</p>"));

        assertThat(entregados.getFirst().textBody()).isEqualTo("texto plano");
    }

    @Test
    void elAdjuntoSobrevive() {
        byte[] ics = "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8);
        transport.send(OutgoingEmail.withAttachment("ana@orion.test", "Asunto", "texto", "<p>Hola.</p>",
                "clase-orion.ics", ics, "text/calendar"));

        OutgoingEmail salida = entregados.getFirst();
        assertThat(salida.hasAttachment()).isTrue();
        assertThat(salida.attachmentFilename()).isEqualTo("clase-orion.ics");
        assertThat(salida.attachmentContent()).isEqualTo(ics);
        assertThat(salida.attachmentContentType()).isEqualTo("text/calendar");
    }

    @Test
    void elDestinatarioYElAsuntoLleganComoVenian() {
        transport.send(OutgoingEmail.plain("ana@orion.test", "Tu clase quedó agendada", "texto", "<p>Hola.</p>"));

        assertThat(entregados.getFirst().to()).isEqualTo("ana@orion.test");
        assertThat(entregados.getFirst().subject()).isEqualTo("Tu clase quedó agendada");
    }

    @Test
    void unCorreoSinHtmlNoRevienta() {
        transport.send(OutgoingEmail.plain("ana@orion.test", "Asunto", "solo texto", null));

        assertThat(entregados.getFirst().htmlBody()).isNull();
    }
}
