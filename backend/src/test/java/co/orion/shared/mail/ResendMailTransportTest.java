package co.orion.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** El cuerpo JSON que se le manda a Resend, sin hacer la llamada HTTP. */
class ResendMailTransportTest {

    @Test
    void buildsThePayloadWithFromToAndBothBodies() {
        Map<String, Object> payload = ResendMailTransport.buildPayload(
                "Orión <notificaciones@orion.test>",
                OutgoingEmail.plain("ana@orion.test", "Asunto", "texto plano", "<p>html</p>"));

        assertThat(payload.get("from")).isEqualTo("Orión <notificaciones@orion.test>");
        assertThat(payload.get("to")).isEqualTo(List.of("ana@orion.test"));
        assertThat(payload.get("subject")).isEqualTo("Asunto");
        assertThat(payload.get("text")).isEqualTo("texto plano");
        assertThat(payload.get("html")).isEqualTo("<p>html</p>");
        assertThat(payload).doesNotContainKey("attachments");
    }

    @Test
    void encodesTheAttachmentAsBase64() {
        byte[] ics = "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> payload = ResendMailTransport.buildPayload(
                "Orión <notificaciones@orion.test>",
                OutgoingEmail.withAttachment("ana@orion.test", "Clase", "t", "<p>h</p>",
                        "clase-orion.ics", ics, "text/calendar"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) payload.get("attachments");
        assertThat(attachments).hasSize(1);
        Map<String, Object> attachment = attachments.getFirst();
        assertThat(attachment.get("filename")).isEqualTo("clase-orion.ics");
        assertThat(attachment.get("content")).isEqualTo(Base64.getEncoder().encodeToString(ics));
        assertThat(attachment.get("content_type")).isEqualTo("text/calendar");
    }
}
