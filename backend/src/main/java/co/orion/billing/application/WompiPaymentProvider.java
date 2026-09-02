package co.orion.billing.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import co.orion.billing.domain.Payment;
import co.orion.shared.error.BusinessRuleViolationException;

/**
 * Wompi (Bancolombia): PSE, tarjeta y Nequi. Se usa el <em>Web Checkout</em> por redirección, no el
 * widget embebido, porque así los datos de la tarjeta nunca pasan por nuestro dominio y el alcance
 * de PCI se queda del lado de Wompi.
 *
 * Dos secretos distintos, con dos usos que no se pueden confundir:
 * el de <b>integridad</b> firma lo que MANDAMOS (que nadie edite el precio en la URL) y el de
 * <b>eventos</b> verifica lo que RECIBIMOS (que nadie nos confirme reservas gratis).
 *
 * Sin SDK, como Resend y Cloudinary: son tres llamadas HTTP y un SHA-256.
 */
@Component
public class WompiPaymentProvider implements PaymentProvider {

    public static final String PROVIDER = "WOMPI";

    /** Wompi no acepta cobros por debajo de este monto. */
    private static final long MIN_CHARGE_COP = 1500;

    private static final String CURRENCY = "COP";

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiBaseUrl;
    private final String checkoutUrl;
    private final String publicKey;
    private final String integritySecret;
    private final String eventsSecret;

    public WompiPaymentProvider(ObjectMapper json,
                                @Value("${orion.payments.wompi.api-base-url}") String apiBaseUrl,
                                @Value("${orion.payments.wompi.checkout-url}") String checkoutUrl,
                                @Value("${orion.payments.wompi.public-key:}") String publicKey,
                                @Value("${orion.payments.wompi.integrity-secret:}") String integritySecret,
                                @Value("${orion.payments.wompi.events-secret:}") String eventsSecret) {
        this.json = json;
        this.apiBaseUrl = apiBaseUrl;
        this.checkoutUrl = checkoutUrl;
        this.publicKey = publicKey;
        this.integritySecret = integritySecret;
        this.eventsSecret = eventsSecret;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public long minimumChargeCop() {
        return MIN_CHARGE_COP;
    }

    @Override
    public PaymentIntent createIntent(Payment payment, String reference, String returnUrl) {
        requireConfigured(publicKey, "WOMPI_PUBLIC_KEY");
        requireConfigured(integritySecret, "WOMPI_INTEGRITY_SECRET");

        long amountInCents = payment.getChargedCop() * 100;
        // El orden de la concatenación es el que Wompi verifica; cambiarlo produce una firma que
        // el checkout rechaza con "La firma es inválida".
        String integrity = sha256Hex(reference + amountInCents + CURRENCY + integritySecret);

        String url = checkoutUrl
                + "?public-key=" + encode(publicKey)
                + "&currency=" + CURRENCY
                + "&amount-in-cents=" + amountInCents
                + "&reference=" + encode(reference)
                + "&signature:integrity=" + integrity
                + "&redirect-url=" + encode(returnUrl);

        return new PaymentIntent(PROVIDER, reference, url, amountInCents);
    }

    /**
     * Verifica el checksum y traduce. El cálculo es SHA-256 sobre la concatenación de: los valores
     * de las rutas que el propio evento lista en {@code signature.properties} —en ese orden, leídas
     * bajo {@code data}—, luego {@code timestamp}, luego el secreto de eventos.
     *
     * Las propiedades se leen del evento y NO se codifican aquí a propósito: Wompi las varía por
     * tipo de evento, y una lista fija dejaría de validar el día que añadan un campo.
     */
    @Override
    public ProviderEvent parseWebhook(String rawBody, Map<String, String> headers) {
        requireConfigured(eventsSecret, "WOMPI_EVENTS_SECRET");

        JsonNode root;
        try {
            root = json.readTree(rawBody);
        } catch (Exception ex) {
            throw new InvalidWebhookSignatureException("Cuerpo del webhook ilegible");
        }

        JsonNode signature = root.path("signature");
        JsonNode properties = signature.path("properties");
        String checksum = signature.path("checksum").asString(null);
        if (checksum == null || !properties.isArray() || properties.isEmpty()) {
            throw new InvalidWebhookSignatureException("El webhook no trae firma");
        }

        JsonNode data = root.path("data");
        StringBuilder concatenated = new StringBuilder();
        for (JsonNode property : properties) {
            concatenated.append(valueAt(data, property.asString("")));
        }
        concatenated.append(root.path("timestamp").asString("")).append(eventsSecret);

        String expected = sha256Hex(concatenated.toString());
        if (!constantTimeEquals(expected, checksum)) {
            throw new InvalidWebhookSignatureException("Checksum del webhook inválido");
        }

        JsonNode transaction = data.path("transaction");
        String transactionId = transaction.path("id").asString(null);
        String status = transaction.path("status").asString(null);
        if (transactionId == null || status == null) {
            throw new InvalidWebhookSignatureException("El webhook no trae transacción");
        }

        return new ProviderEvent(
                PROVIDER,
                // Wompi no manda un id de evento. La pareja (transacción, estado) SÍ identifica el
                // hecho: un reenvío del mismo estado es el mismo hecho, y el paso PENDING→APPROVED
                // son dos hechos distintos que sí queremos registrar por separado.
                transactionId + ":" + status,
                root.path("event").asString("transaction.updated"),
                transactionId,
                transaction.path("reference").asString(null),
                parseStatus(status),
                transaction.path("amount_in_cents").asLong(0),
                rawBody);
    }

    @Override
    public ProviderTransaction fetchTransaction(String transactionId) {
        requireConfigured(publicKey, "WOMPI_PUBLIC_KEY");

        JsonNode body = http.get()
                .uri(apiBaseUrl + "/transactions/" + transactionId)
                .header("Authorization", "Bearer " + publicKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessRuleViolationException(
                            "Wompi no devolvió la transacción " + transactionId
                            + " (" + response.getStatusCode() + ")");
                })
                .body(JsonNode.class);

        JsonNode transaction = body != null ? body.path("data") : null;
        if (transaction == null || transaction.isMissingNode()) {
            throw new BusinessRuleViolationException("Wompi devolvió una transacción vacía");
        }
        return new ProviderTransaction(
                transaction.path("id").asString(null),
                transaction.path("reference").asString(null),
                parseStatus(transaction.path("status").asString("PENDING")),
                transaction.path("amount_in_cents").asLong(0));
    }

    /** Resuelve una ruta como "transaction.amount_in_cents" contra el nodo {@code data}. */
    private String valueAt(JsonNode data, String path) {
        JsonNode current = data;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current.asString("");
    }

    private ProviderTransactionStatus parseStatus(String status) {
        try {
            return ProviderTransactionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Un estado que no conocemos se trata como "todavía nada": nunca como aprobado.
            return ProviderTransactionStatus.PENDING;
        }
    }

    private void requireConfigured(String secret, String envVar) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessRuleViolationException(
                    "La pasarela no está configurada (falta " + envVar + ")");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    /**
     * Comparación sin fuga de tiempo. Wompi manda el checksum en mayúsculas y la firma calculada
     * sale en minúsculas: se normaliza antes, no se comparan mayúsculas con minúsculas.
     */
    private static boolean constantTimeEquals(String expected, String received) {
        return MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
