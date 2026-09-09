package co.orion.scheduling.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Sala Jitsi pública: abre en el navegador, sin cuenta y sin costo.
 *
 * <p><strong>Lo que esta sala NO puede darte.</strong> En {@code meet.jit.si} manda quien entra
 * primero. Si el estudiante llega antes que el profesor, es él quien puede silenciar, expulsar y
 * cerrar la reunión. No hay forma de impedirlo desde la URL: el control de moderador exige un
 * token firmado, y eso solo lo dan las versiones de pago (Jitsi JaaS) o una plataforma como Zoom.
 * Está comparado en {@code docs/videollamada-opciones.md}; mientras tanto, esto es lo que sí se
 * puede apretar sin cuenta nueva.
 *
 * <p><strong>El nombre de sala completo.</strong> Antes eran 8 caracteres hexadecimales: 32 bits,
 * que para un nombre de sala pública es poco — quien quiera probar nombres entra en salas ajenas.
 * Ahora va el id entero de la reserva.
 *
 * <p><strong>La antesala.</strong> {@code prejoinPageEnabled} obliga a pasar por la pantalla previa
 * en vez de caer dentro con el micrófono abierto, y {@code disableInviteFunctions} quita el botón
 * de invitar: la sala es de esa clase y de nadie más.
 */
@Component
public class JitsiMeetingLinkProvider implements MeetingLinkProvider {

    private static final String BASE = "https://meet.jit.si/OrionIdiomas-";

    /** Ajustes que Jitsi acepta por fragmento de URL, sin cuenta ni token. */
    private static final String CONFIG =
            "#config.prejoinPageEnabled=true"
            + "&config.disableInviteFunctions=true"
            + "&config.startWithVideoMuted=false"
            + "&config.startWithAudioMuted=false";

    @Override
    public String linkFor(UUID bookingId) {
        return BASE + bookingId.toString().replace("-", "") + CONFIG;
    }
}
