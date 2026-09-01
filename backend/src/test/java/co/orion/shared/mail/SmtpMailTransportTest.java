package co.orion.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Ejercita el transporte SMTP contra un {@link MimeMessage} real. El correo lleva HTML con
 * alternativa de texto ({@code setText(plano, html)}), que exige modo multipart: sin él lanzaría
 * "Not in multipart mode" y no saldría. Este test falla si {@code send()} no llega a invocarse.
 */
class SmtpMailTransportTest {

    private JavaMailSender mailSender;
    private SmtpMailTransport transport;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        transport = new SmtpMailTransport(mailSender, "notificaciones@orion.test");
    }

    @Test
    void sendsAMultipartMessageWithTheHtmlAndLink() throws Exception {
        transport.send(OutgoingEmail.plain(
                "profe@orion.test", "Sofía te invita",
                "texto plano con https://app.orion.test/invitacion?token=abc",
                "<p><a href=\"https://app.orion.test/invitacion?token=abc\">Aceptar</a></p>"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        String body = asString(captor.getValue());
        assertThat(body).contains("multipart");
        assertThat(body).contains("invitacion?token");
    }

    @Test
    void attachesTheIcsWhenPresent() throws Exception {
        transport.send(OutgoingEmail.withAttachment(
                "ana@orion.test", "Tu clase quedó agendada",
                "texto", "<p>html</p>",
                "clase-orion.ics", "BEGIN:VCALENDAR".getBytes(StandardCharsets.UTF_8), "text/calendar"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        assertThat(asString(captor.getValue())).contains("clase-orion.ics");
    }

    private String asString(MimeMessage message) throws Exception {
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8).replace("=\r\n", "");
    }
}
