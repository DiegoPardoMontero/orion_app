package co.orion.notifications.application;

/** Un correo listo para enviar. El .ics es null cuando no lleva adjunto (cancelaciones). */
public record BookingEmail(String to, String subject, String html, String text, String ics) {
}
