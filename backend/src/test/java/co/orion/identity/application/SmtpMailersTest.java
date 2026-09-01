package co.orion.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
 * Ejercita las implementaciones SMTP reales (no las interfaces mockeadas de los ITs) contra un
 * {@link MimeMessage} de verdad. Los mailers construyen un mensaje HTML con alternativa de texto
 * plano vía {@code setText(plano, html)}, que exige modo multipart: con el helper en modo simple
 * lanzaría "Not in multipart mode" y —como el error se traga y se loguea— el correo nunca saldría
 * sin que nada fallara a gritos. Este test verifica que {@code send()} llega a invocarse, es decir,
 * que la composición del mensaje no explotó por el camino.
 */
class SmtpMailersTest {

    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        // MimeMessage real: si el helper compone mal el cuerpo, setText lanza antes de send().
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void theInviteMailerComposesAndSendsAMultipartMessage() throws Exception {
        new SmtpProfessorInviteMailer(mailSender, "notificaciones@orion.test")
                .sendInvite("profe@orion.test", "https://app.orion.test/invitacion?token=abc123");

        String body = asString(captureSent());
        // El cuerpo serializado prueba la estructura multipart (plano + HTML) que setText compone.
        assertThat(body).contains("multipart");
        assertThat(body).contains("Aceptar la invitaci");
        assertThat(body).contains("invitacion?token");
    }

    @Test
    void theResetMailerComposesAndSendsAMultipartMessage() throws Exception {
        new SmtpPasswordResetMailer(mailSender, "notificaciones@orion.test")
                .sendResetLink("ana@orion.test", "Ana Ramirez", "https://app.orion.test/restablecer?token=xyz789");

        String body = asString(captureSent());
        assertThat(body).contains("multipart");
        assertThat(body).contains("Restablecer");
        assertThat(body).contains("restablecer?token");
    }

    private MimeMessage captureSent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        // Si la composición hubiera lanzado, send() nunca se habría llamado y esto fallaría.
        verify(mailSender, times(1)).send(captor.capture());
        return captor.getValue();
    }

    private String asString(MimeMessage message) throws Exception {
        // Transport.send() haría esto en producción; con send() mockeado lo hacemos a mano para que
        // el Content-Type multipart y el cuerpo queden materializados en la serialización.
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        // El cuerpo viaja en quoted-printable; deshacemos los cortes blandos para poder buscar texto.
        return out.toString(StandardCharsets.UTF_8).replace("=\r\n", "");
    }
}
