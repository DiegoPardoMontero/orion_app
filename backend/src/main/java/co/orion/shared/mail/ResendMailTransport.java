package co.orion.shared.mail;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Transporte por la API HTTP de Resend (POST https://api.resend.com/emails, sobre 443). Producción
 * lo usa en vez de SMTP porque Railway —como casi toda PaaS— bloquea los puertos SMTP salientes:
 * el 587 daba "Connect timed out". HTTPS nunca se bloquea. Sin SDK, igual que la subida a Cloudinary.
 */
@Component
@Qualifier("entrega")
@ConditionalOnProperty(name = "orion.mail.transport", havingValue = "resend")
public class ResendMailTransport implements MailTransport {

    private static final String API_URL = "https://api.resend.com/emails";

    private final RestClient http;
    private final String apiKey;
    private final String from;

    public ResendMailTransport(@Value("${orion.mail.resend.api-key:}") String apiKey,
                               @Value("${orion.mail.from}") String from) {
        this.apiKey = apiKey;
        this.from = from;

        // Timeouts explícitos: un HTTP colgado tampoco debe colgar nada (el envío es async, pero aun así).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(OutgoingEmail email) {
        if (apiKey.isBlank()) {
            throw new MailDeliveryException("Resend no está configurado (falta RESEND_API_KEY)");
        }
        try {
            http.post()
                    .uri(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(from, email))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String body = new String(response.getBody().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                        throw new MailDeliveryException("Resend rechazó el correo a " + email.to()
                                + " (" + response.getStatusCode() + "): " + body);
                    })
                    .toBodilessEntity();
        } catch (MailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MailDeliveryException("No se pudo enviar el correo por Resend a " + email.to(), ex);
        }
    }

    /** Cuerpo JSON que espera Resend. Extraído para poder probarlo sin hacer la llamada HTTP. */
    static Map<String, Object> buildPayload(String from, OutgoingEmail email) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(email.to()));
        payload.put("subject", email.subject());
        payload.put("text", email.textBody());
        payload.put("html", email.htmlBody());
        if (email.hasAttachment()) {
            Map<String, Object> attachment = new java.util.LinkedHashMap<>();
            attachment.put("filename", email.attachmentFilename());
            attachment.put("content", Base64.getEncoder().encodeToString(email.attachmentContent()));
            if (email.attachmentContentType() != null) {
                attachment.put("content_type", email.attachmentContentType());
            }
            payload.put("attachments", List.of(attachment));
        }
        return payload;
    }
}
