package co.orion.scheduling.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * La sala de clase, dentro de Orión.
 *
 * <p>Ya no es una URL a un tercero: es una ruta de la propia aplicación. El estudiante no sale de
 * la plataforma, y la videollamada se monta embebida con el IFrame API de JaaS contra un token que
 * firma Orión. Lo que antes era un enlace público que cualquiera podía reenviar ahora es una puerta
 * que comprueba quién llama, si la clase está confirmada y si estamos dentro de la ventana.
 *
 * <p>Sustituye a {@code JitsiMeetingLinkProvider}, que se retiró con todo lo que documentaba: en
 * {@code meet.jit.si} mandaba quien entrara primero, y no había forma de arreglarlo desde la URL.
 */
@Component
public class JaasMeetingLinkProvider implements MeetingLinkProvider {

    @Override
    public String linkFor(UUID bookingId) {
        return "/mis-clases/" + bookingId + "/aula";
    }
}
