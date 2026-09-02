package co.orion.messaging.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.orion.messaging.domain.FlaggedReason;

/**
 * La política de contacto en forma pura: sin Spring, sin estado, sin reloj. Recibe un texto y
 * devuelve el texto enmascarado más la razón por la que se marcó (o {@code null} si estaba limpio).
 * El mensaje SÍ se entrega, solo que con la info de contacto oculta.
 *
 * Al ser una función pura se puede probar exhaustivamente: teléfonos, correos, menciones de canales
 * externos, texto limpio y casos límite, todo sin levantar nada.
 *
 * Prioridad de la razón: un teléfono o un correo (CONTACT_INFO) pesa más que la mera mención de un
 * canal externo (OFF_PLATFORM); si solo hay mención de canal, la razón es OFF_PLATFORM.
 */
public final class ContactMasker {

    public static final String PHONE_MASK = "[número oculto]";
    public static final String EMAIL_MASK = "[correo oculto]";
    public static final String CHANNEL_MASK = "[canal externo]";

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    // Una corrida que empieza y termina en dígito, admitiendo espacios y separadores telefónicos
    // en medio. Cuántos dígitos tiene de verdad se cuenta aparte: solo se enmascara con 7 o más.
    private static final Pattern PHONE_RUN = Pattern.compile("\\+?\\d[\\d\\s().\\-]*\\d");
    private static final int MIN_PHONE_DIGITS = 7;

    // Menciones de canales/gestos para llevar la conversación fuera de Orión.
    private static final Pattern CHANNEL = Pattern.compile(
            "\\b(?:whatsapp|whats\\s?app|wasap|wsp|wpp|telegram|instagram|signal|"
                    + "ll[aá]mame|m[aá]rcame|escr[ií]beme\\s+a)\\b|wa\\.me|t\\.me|"
                    + "\\bmi\\s+(?:n[uú]mero|celular|cel|tel[eé]fono|correo|email)\\b",
            Pattern.CASE_INSENSITIVE);

    private ContactMasker() {
    }

    /** El resultado: el texto ya enmascarado y la razón (null = nada que ocultar). */
    public record MaskResult(String masked, FlaggedReason reason) {

        public boolean wasMasked() {
            return reason != null;
        }
    }

    public static MaskResult mask(String input) {
        if (input == null || input.isBlank()) {
            return new MaskResult(input, null);
        }

        boolean contactInfo = false;
        boolean channel = false;
        String result = input;

        // Correos primero: un correo contiene texto que el barrido telefónico podría trocear.
        StringBuilder emails = new StringBuilder();
        Matcher emailMatcher = EMAIL.matcher(result);
        while (emailMatcher.find()) {
            contactInfo = true;
            emailMatcher.appendReplacement(emails, Matcher.quoteReplacement(EMAIL_MASK));
        }
        emailMatcher.appendTail(emails);
        result = emails.toString();

        // Teléfonos: corridas con 7+ dígitos reales.
        StringBuilder phones = new StringBuilder();
        Matcher phoneMatcher = PHONE_RUN.matcher(result);
        while (phoneMatcher.find()) {
            String run = phoneMatcher.group();
            if (countDigits(run) >= MIN_PHONE_DIGITS) {
                contactInfo = true;
                phoneMatcher.appendReplacement(phones, Matcher.quoteReplacement(PHONE_MASK));
            } else {
                phoneMatcher.appendReplacement(phones, Matcher.quoteReplacement(run));
            }
        }
        phoneMatcher.appendTail(phones);
        result = phones.toString();

        // Menciones de canales externos.
        StringBuilder channels = new StringBuilder();
        Matcher channelMatcher = CHANNEL.matcher(result);
        while (channelMatcher.find()) {
            channel = true;
            channelMatcher.appendReplacement(channels, Matcher.quoteReplacement(CHANNEL_MASK));
        }
        channelMatcher.appendTail(channels);
        result = channels.toString();

        FlaggedReason reason = contactInfo
                ? FlaggedReason.CONTACT_INFO
                : (channel ? FlaggedReason.OFF_PLATFORM : null);

        return new MaskResult(result, reason);
    }

    private static int countDigits(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
