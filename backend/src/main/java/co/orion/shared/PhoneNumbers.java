package co.orion.shared;

/**
 * Normalización central de teléfonos a formato E.164 (`+57300...`). El frontend ya envía E.164
 * (selector de país + número), pero esto es la red de seguridad para cualquier otra entrada y para
 * el backfill de datos viejos. No inventa indicativos: si no puede inferirlo con confianza, deja
 * el número tal cual (mejor esfuerzo). Los links `wa.me` solo necesitan los dígitos, así que un
 * E.164 con `+` funciona igual.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String toE164(String raw) {
        if (raw == null) {
            return null;
        }
        // Solo dígitos y un posible '+' inicial.
        String cleaned = raw.replaceAll("[^\\d+]", "");
        if (cleaned.isBlank() || cleaned.equals("+")) {
            return null;
        }
        if (cleaned.startsWith("+")) {
            return "+" + cleaned.substring(1).replaceAll("\\D", "");
        }
        // Heurística de celular colombiano: 10 dígitos que empiezan por 3 → +57.
        if (cleaned.matches("^3\\d{9}$")) {
            return "+57" + cleaned;
        }
        // Sin '+' y sin poder inferir el indicativo: se deja tal cual, no se inventa.
        return cleaned;
    }
}
