package co.orion.shared.mail;

/**
 * Un correo listo para transmitir, agnóstico del transporte. Lleva cuerpo HTML con alternativa de
 * texto plano y, opcionalmente, un único adjunto (hoy solo el {@code .ics} de las reservas).
 */
public record OutgoingEmail(
        String to,
        String subject,
        String textBody,
        String htmlBody,
        String attachmentFilename,
        byte[] attachmentContent,
        String attachmentContentType) {

    /** Correo sin adjunto (recuperación, invitación, cancelaciones). */
    public static OutgoingEmail plain(String to, String subject, String textBody, String htmlBody) {
        return new OutgoingEmail(to, subject, textBody, htmlBody, null, null, null);
    }

    /** Correo con un adjunto (la confirmación de reserva lleva el {@code .ics}). */
    public static OutgoingEmail withAttachment(String to, String subject, String textBody, String htmlBody,
                                               String filename, byte[] content, String contentType) {
        return new OutgoingEmail(to, subject, textBody, htmlBody, filename, content, contentType);
    }

    public boolean hasAttachment() {
        return attachmentFilename != null && attachmentContent != null;
    }
}
