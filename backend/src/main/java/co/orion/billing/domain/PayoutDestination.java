package co.orion.billing.domain;

import java.util.Locale;

/**
 * A dónde se le transfiere a un profe: la llave Bre-B, el documento y el titular, validados y
 * normalizados. Clase pura (sin Spring ni base) para poder probar cada formato por separado.
 *
 * <p>Lo que no se puede validar desde aquí es que la llave esté a nombre del profe: eso lo confirma
 * el admin al pagar, comparando el nombre que le muestra su banco con {@link #holderName()}.
 */
public record PayoutDestination(BreBKeyType keyType, String keyValue, IdDocumentType documentType,
                                String documentNumber, String holderName) {

    /** Valida y normaliza lo que escribió el profe. Lanza {@link IllegalArgumentException} con un mensaje para él. */
    public static PayoutDestination of(BreBKeyType keyType, String key, IdDocumentType documentType,
                                       String documentNumber, String holderName) {
        if (keyType == null) {
            throw new IllegalArgumentException("Elige el tipo de llave.");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("Elige el tipo de documento.");
        }
        String titular = holderName == null ? "" : holderName.trim().replaceAll("\\s+", " ");
        if (titular.length() < 3 || titular.length() > 150) {
            throw new IllegalArgumentException("Escribe el nombre completo del titular, como aparece en tu banco.");
        }
        return new PayoutDestination(keyType, normalizarLlave(keyType, key), documentType,
                normalizarDocumento(documentType, documentNumber), titular);
    }

    static String normalizarLlave(BreBKeyType tipo, String crudo) {
        String valor = crudo == null ? "" : crudo.trim();
        return switch (tipo) {
            case PHONE -> {
                String digitos = valor.replaceAll("[\\s()+-]", "");
                if (digitos.startsWith("57") && digitos.length() == 12) {
                    digitos = digitos.substring(2);
                }
                if (!digitos.matches("^3\\d{9}$")) {
                    throw new IllegalArgumentException("La llave de celular son los 10 dígitos de tu celular, empezando por 3.");
                }
                yield digitos;
            }
            case ID_NUMBER -> {
                String digitos = valor.replaceAll("[\\s.]", "");
                if (!digitos.matches("^\\d{5,11}$")) {
                    throw new IllegalArgumentException("La llave de cédula es tu número de documento, solo dígitos.");
                }
                yield digitos;
            }
            case EMAIL -> {
                String correo = valor.toLowerCase(Locale.ROOT);
                if (correo.length() > 100 || !correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new IllegalArgumentException("Escribe el correo de tu llave completo, como nombre@dominio.com.");
                }
                yield correo;
            }
            case ALPHANUMERIC -> {
                String sinArroba = valor.startsWith("@") ? valor.substring(1) : valor;
                if (!sinArroba.matches("^[A-Za-z0-9._-]{3,30}$")) {
                    throw new IllegalArgumentException("La llave alfanumérica empieza por @ y lleva letras o números, sin espacios.");
                }
                yield "@" + sinArroba;
            }
        };
    }

    static String normalizarDocumento(IdDocumentType tipo, String crudo) {
        String valor = crudo == null ? "" : crudo.trim().replaceAll("[\\s.-]", "");
        if (tipo == IdDocumentType.PAS) {
            if (!valor.matches("^[A-Za-z0-9]{5,15}$")) {
                throw new IllegalArgumentException("Escribe el número de pasaporte, con letras y números.");
            }
            return valor.toUpperCase(Locale.ROOT);
        }
        if (!valor.matches("^\\d{5,11}$")) {
            throw new IllegalArgumentException("Escribe el número de documento, solo dígitos.");
        }
        return valor;
    }

    /** La llave como la ve el profe (y cualquiera que no sea el admin pagando): sin el dato completo. */
    public String maskedKey() {
        return enmascararLlave(keyType, keyValue);
    }

    public String maskedDocument() {
        return "••••" + ultimos(documentNumber, 3);
    }

    public static String enmascararLlave(BreBKeyType tipo, String valor) {
        return switch (tipo) {
            case PHONE, ID_NUMBER -> "••••" + ultimos(valor, 4);
            case EMAIL -> {
                int arroba = valor.indexOf('@');
                yield valor.charAt(0) + "•••" + valor.substring(arroba);
            }
            case ALPHANUMERIC -> valor.substring(0, Math.min(3, valor.length())) + "•••";
        };
    }

    private static String ultimos(String valor, int n) {
        return valor.length() <= n ? valor : valor.substring(valor.length() - n);
    }
}
