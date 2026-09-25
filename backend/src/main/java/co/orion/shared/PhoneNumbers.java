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

    /**
     * Si el número, ya normalizado, es un WhatsApp al que se puede escribir: E.164 completo (de 8 a
     * 15 dígitos con el indicativo) y, en Colombia, un celular (+57 y diez dígitos que empiezan por
     * 3). Es la misma regla que aplica el formulario (`whatsappValido` en `lib/phone.ts`).
     */
    public static boolean esWhatsappValido(String e164) {
        if (e164 == null || !e164.matches("^\\+\\d{8,15}$")) {
            return false;
        }
        return !e164.startsWith("+57") || e164.matches("^\\+573\\d{9}$");
    }
}
