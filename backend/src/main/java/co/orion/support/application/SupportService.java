package co.orion.support.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.support.domain.SupportMessage;
import co.orion.support.domain.SupportTicket;
import co.orion.support.domain.TicketCategory;
import co.orion.support.domain.TicketStatus;
import co.orion.support.persistence.SupportMessageRepository;
import co.orion.support.persistence.SupportTicketRepository;

/**
 * Los tickets de soporte.
 *
 * <p>El módulo {@code support} no depende de ningún otro salvo {@code shared}: guarda un
 * {@code UUID userId} plano y un {@code bookingId} plano, y la integridad la ponen las FK de la
 * base. Es la misma regla que sigue {@code scheduling} con los profesores, y aquí importa más:
 * soporte tiene que poder recibir un reclamo sobre cualquier cosa sin conocer a nadie.
 */
@Service
public class SupportService {

    /** Sin vocales: un código legible por teléfono no debe poder formar una palabra. */
    private static final char[] ALFABETO = "0123456789BCDFGHJKLMNPQRSTVWXZ".toCharArray();

    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final BookingRepository bookings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SupportService(SupportTicketRepository tickets,
                          SupportMessageRepository messages,
                          BookingRepository bookings,
                          Clock clock) {
        this.tickets = tickets;
        this.messages = messages;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional
    public Hilo abrir(UUID userId, TicketCategory category, String subject, String body,
                      UUID bookingId) {
        // La clase que se cita tiene que ser de quien escribe. Si no, un reclamo podría llegarle al
        // admin como si fuera sobre la clase de otra persona —y pedir un reembolso por ella—.
        if (bookingId != null && bookings.findById(bookingId)
                .filter(b -> b.getStudentId().equals(userId) || b.getProfessorId().equals(userId))
                .isEmpty()) {
            throw new UnprocessableException("Esa clase no aparece entre las tuyas");
        }
        Instant now = clock.instant();
        SupportTicket ticket = new SupportTicket(nuevoCodigo(), userId, category, subject,
                bookingId, now);
        SupportTicket guardado = tickets.saveAndFlush(ticket);
        messages.save(new SupportMessage(guardado.getId(), userId, body));
        return hilo(guardado);
    }

    /** Lo que ve quien escribió: solo lo suyo. */
    @Transactional(readOnly = true)
    public List<SupportTicket> mios(UUID userId) {
        return tickets.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Hilo verComoDueno(UUID userId, String code) {
        SupportTicket ticket = porCodigo(code);
        if (!ticket.getUserId().equals(userId)) {
            // 404 y no 403: confirmar que el ticket existe ya sería decir algo de otra persona.
            throw new ResourceNotFoundException("No encontramos esa solicitud");
        }
        return hilo(ticket);
    }

    @Transactional(readOnly = true)
    public Hilo verComoAdmin(String code) {
        return hilo(porCodigo(code));
    }

    /** La bandeja del administrador: lo que vence antes, primero. */
    @Transactional(readOnly = true)
    public List<SupportTicket> bandeja() {
        return tickets.findAbiertosPorUrgencia();
    }

    @Transactional(readOnly = true)
    public long abiertos() {
        return tickets.countByStatus(TicketStatus.OPEN);
    }

    /**
     * Responde en el hilo. El estado lo decide quién escribe, no un parámetro: si contesta Orión
     * queda respondida; si contesta quien la abrió, vuelve a estar pendiente —incluso si estaba
     * cerrada—. Dar por zanjado algo que la otra persona no da por zanjado convierte un soporte
     * en un muro.
     */
    @Transactional
    public Hilo responder(UUID authorId, String code, String body, boolean esAdmin) {
        SupportTicket ticket = porCodigo(code);
        if (!esAdmin && !ticket.getUserId().equals(authorId)) {
            throw new ResourceNotFoundException("No encontramos esa solicitud");
        }

        messages.save(new SupportMessage(ticket.getId(), authorId, body));
        if (esAdmin) {
            ticket.markAnswered();
        } else {
            ticket.markReopened();
        }
        tickets.save(ticket);
        return hilo(ticket);
    }

    /** Cerrar es del administrador. Quien la abrió la reabre escribiendo, no pulsando un botón. */
    @Transactional
    public Hilo cerrar(String code) {
        SupportTicket ticket = porCodigo(code);
        if (ticket.isClosed()) {
            throw new UnprocessableException("Esa solicitud ya estaba cerrada.");
        }
        ticket.close();
        tickets.save(ticket);
        return hilo(ticket);
    }

    private SupportTicket porCodigo(String code) {
        return tickets.findByCode(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos esa solicitud"));
    }

    private Hilo hilo(SupportTicket ticket) {
        return new Hilo(ticket, messages.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    /**
     * «ORN-4F2A19». Se reintenta ante colisión en vez de confiar en que no la haya: con 30^6
     * combinaciones es improbable, y «improbable» no es «imposible» cuando hay un índice único
     * esperando para reventar la petición de alguien.
     */
    private String nuevoCodigo() {
        for (int intento = 0; intento < 10; intento++) {
            StringBuilder sb = new StringBuilder("ORN-");
            for (int i = 0; i < 6; i++) {
                sb.append(ALFABETO[random.nextInt(ALFABETO.length)]);
            }
            String code = sb.toString();
            if (!tickets.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de ticket libre");
    }

    /** El ticket con su conversación. */
    public record Hilo(SupportTicket ticket, List<SupportMessage> mensajes) {
    }
}
