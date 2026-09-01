package co.orion.scheduling.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Sala Jitsi (abre en el navegador, sin cuenta). El nombre de sala lleva el id de la reserva para
 * que sea única y difícil de adivinar: `meet.jit.si/OrionIdiomas-<8 hex del id>`.
 */
@Component
public class JitsiMeetingLinkProvider implements MeetingLinkProvider {

    private static final String BASE = "https://meet.jit.si/OrionIdiomas-";

    @Override
    public String linkFor(UUID bookingId) {
        String slug = bookingId.toString().replace("-", "").substring(0, 8);
        return BASE + slug;
    }
}
