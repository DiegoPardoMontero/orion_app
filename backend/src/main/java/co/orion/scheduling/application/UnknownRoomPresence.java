package co.orion.scheduling.application;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Lo que se sabe de la sala mientras no haya webhooks de JaaS: nada.
 *
 * <p>Contesta siempre que no, y eso es correcto: el diseño de la antesala ya contempla «aún no ha
 * entrado» como estado normal. Inventar presencia desde el cliente sería peor — el navegador de una
 * persona no puede afirmar que la otra está en la sala.
 */
@Component
@ConditionalOnMissingBean(name = "webhookRoomPresence")
public class UnknownRoomPresence implements RoomPresence {

    @Override
    public boolean estaDentro(UUID bookingId, UUID userId) {
        return false;
    }
}
