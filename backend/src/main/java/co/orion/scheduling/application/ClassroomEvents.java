package co.orion.scheduling.application;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.RoomParticipations;

/**
 * Lo que 8x8 cuenta de cada sala: quién entró, quién salió y cuánto habló cada uno.
 *
 * <p>Solo se cree lo que se puede atar a algo nuestro. El evento tiene que ser de nuestra app de
 * JaaS, la sala tiene que ser una reserva que existe (el nombre de sala ES el id de la reserva) y
 * la persona tiene que ser el profesor o el estudiante de esa reserva (su id viaja en el token que
 * firma Orión). Cualquier otra cosa se ignora sin error: 8x8 no tiene nada que corregir y
 * reintentaría para siempre.
 *
 * <p>Idempotente por la llave de 8x8: un evento reenviado se procesa una vez.
 */
@Service
public class ClassroomEvents {

    private static final Logger log = LoggerFactory.getLogger(ClassroomEvents.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JaasProperties props;
    private final BookingRepository bookings;
    private final RoomParticipations participaciones;

    public ClassroomEvents(JaasProperties props, BookingRepository bookings,
                           RoomParticipations participaciones) {
        this.props = props;
        this.bookings = bookings;
        this.participaciones = participaciones;
    }

    @Transactional
    public void procesar(String cuerpo) {
        JsonNode evento;
        try {
            evento = JSON.readTree(cuerpo);
        } catch (Exception ex) {
            log.warn("Webhook de JaaS ilegible; se ignora.");
            return;
        }
        String tipo = evento.path("eventType").asText("");
        String llave = evento.path("idempotencyKey").asText("");
        if (llave.isBlank() || !props.appId().equals(evento.path("appId").asText(""))) {
            return;
        }
        Optional<Booking> reserva = reservaDe(evento.path("fqn").asText(""));
        if (reserva.isEmpty()) {
            return;
        }
        if (!tipo.equals("PARTICIPANT_JOINED") && !tipo.equals("PARTICIPANT_LEFT")
                && !tipo.equals("SPEAKER_STATS")) {
            return;
        }
        if (!participaciones.primeraVez(llave, tipo)) {
            return;
        }

        Booking b = reserva.get();
        Instant cuando = Instant.ofEpochMilli(evento.path("timestamp").asLong(System.currentTimeMillis()));
        JsonNode data = evento.path("data");
        switch (tipo) {
            case "PARTICIPANT_JOINED" -> participanteDe(b, data.path("id").asText(""))
                    .ifPresent(u -> participaciones.entro(b.getId(), u, cuando));
            case "PARTICIPANT_LEFT" -> participanteDe(b, data.path("id").asText(""))
                    .ifPresent(u -> participaciones.salio(b.getId(), u, cuando));
            default -> {
                // SPEAKER_STATS: un objeto por conexión (jid), cada una con el id de la persona.
                Iterator<Map.Entry<String, JsonNode>> it = data.fields();
                while (it.hasNext()) {
                    JsonNode stats = it.next().getValue();
                    participanteDe(b, stats.path("id").asText(""))
                            .ifPresent(u -> participaciones.hablo(b.getId(), u,
                                    Math.max(0, stats.path("time").asLong(0)), cuando));
                }
            }
        }
    }

    /** El fqn es «{appId}/{sala}», y la sala es el id de la reserva. */
    private Optional<Booking> reservaDe(String fqn) {
        int barra = fqn.lastIndexOf('/');
        if (barra < 0 || !fqn.substring(0, barra).equals(props.appId())) {
            return Optional.empty();
        }
        try {
            return bookings.findById(UUID.fromString(fqn.substring(barra + 1)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Solo el profesor y el estudiante de esa clase cuentan; cualquier otro id se ignora. */
    private static Optional<UUID> participanteDe(Booking b, String id) {
        try {
            UUID u = UUID.fromString(id);
            return u.equals(b.getProfessorId()) || u.equals(b.getStudentId())
                    ? Optional.of(u) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
