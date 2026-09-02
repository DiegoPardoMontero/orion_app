package co.orion.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Construye eventos de Wompi FIRMADOS como los firma Wompi: SHA-256 sobre los valores que el propio
 * evento lista en {@code signature.properties}, más el timestamp, más el secreto de eventos.
 *
 * El secreto es el falso de {@code src/test/resources/application.properties}. Firmar de verdad en
 * los tests es el punto: un helper que se saltara la firma dejaría sin probar justo lo que impide
 * que cualquiera confirme reservas gratis.
 */
public final class WompiWebhooks {

    public static final String EVENTS_SECRET = "test_events_orion";

    private static final String PROPERTIES =
            "\"transaction.id\",\"transaction.status\",\"transaction.amount_in_cents\"";

    private WompiWebhooks() {
    }

    public static String signed(String transactionId,
                                String reference,
                                String status,
                                long amountInCents,
                                long timestamp) {
        String checksum = sha256Hex(transactionId + status + amountInCents + timestamp + EVENTS_SECRET);
        return body(transactionId, reference, status, amountInCents, timestamp, checksum);
    }

    /** El mismo evento con un checksum que no cuadra: la base no se debe tocar. */
    public static String tampered(String transactionId,
                                  String reference,
                                  String status,
                                  long amountInCents,
                                  long timestamp) {
        return body(transactionId, reference, status, amountInCents, timestamp,
                "0000000000000000000000000000000000000000000000000000000000000000");
    }

    private static String body(String transactionId,
                               String reference,
                               String status,
                               long amountInCents,
                               long timestamp,
                               String checksum) {
        return """
               {"event":"transaction.updated",
                "data":{"transaction":{"id":"%s","reference":"%s","status":"%s",
                        "amount_in_cents":%d,"currency":"COP"}},
                "environment":"test",
                "signature":{"properties":[%s],"checksum":"%s"},
                "timestamp":%d,
                "sent_at":"2026-09-02T12:00:00.000Z"}
               """.formatted(transactionId, reference, status, amountInCents,
                             PROPERTIES, checksum, timestamp);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
