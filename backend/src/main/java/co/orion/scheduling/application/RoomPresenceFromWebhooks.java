package co.orion.scheduling.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import co.orion.scheduling.persistence.RoomParticipations;

/**
 * Quién está dentro de una sala, según lo que 8x8 contó por webhook.
 *
 * <p>Sin webhooks configurados la tabla está vacía y responde que no hay nadie, que es lo mismo que
 * respondía antes y sigue siendo la única respuesta honesta: el navegador de una persona no puede
 * afirmar que la otra está dentro.
 */
@Component
public class RoomPresenceFromWebhooks implements RoomPresence {

    private final RoomParticipations participaciones;

    public RoomPresenceFromWebhooks(RoomParticipations participaciones) {
        this.participaciones = participaciones;
    }

    @Override
    public boolean estaDentro(UUID bookingId, UUID userId) {
        return participaciones.estaDentro(bookingId, userId);
    }
}
