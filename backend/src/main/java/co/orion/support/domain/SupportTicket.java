package co.orion.support.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una solicitud de soporte, con su constancia de fecha y su hilo.
 *
 * <p>El vencimiento se sella al crearla y no se recalcula: el plazo corre desde que la petición
 * entró, no desde la última vez que alguien la miró.
 */
@Entity
@Table(name = "support_tickets")
@EntityListeners(AuditingEntityListener.class)
public class SupportTicket {

    public static final int MAX_ASUNTO = 160;

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, updatable = false, length = 12)
    private String code;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 30)
    private TicketCategory category;

    @Column(name = "subject", nullable = false, length = MAX_ASUNTO)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "due_at", updatable = false)
    private Instant dueAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupportTicket() {
        // exigido por JPA
    }

    public SupportTicket(String code, UUID userId, TicketCategory category, String subject,
                         UUID bookingId, Instant now) {
        this.code = Objects.requireNonNull(code, "code");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.category = Objects.requireNonNull(category, "category");
        this.subject = requireAsunto(subject);
        this.bookingId = bookingId;
        this.dueAt = category.vencimientoDesde(now).orElse(null);
    }

    private static String requireAsunto(String subject) {
        String limpio = subject == null ? "" : subject.trim();
        if (limpio.isEmpty()) {
            throw new UnprocessableException("Cuéntanos en una línea de qué se trata.");
        }
        if (limpio.length() > MAX_ASUNTO) {
            throw new UnprocessableException(
                    "El asunto no puede pasar de " + MAX_ASUNTO + " caracteres.");
        }
        return limpio;
    }

    /** Orión contestó. Sigue abierta para quien la escribió: puede responder y vuelve a OPEN. */
    public void markAnswered() {
        if (status != TicketStatus.CLOSED) {
            this.status = TicketStatus.ANSWERED;
        }
    }

    /**
     * Quien la abrió escribió otra vez. Reabre incluso una cerrada: dar por zanjado algo que la
     * otra persona no da por zanjado es lo que convierte un soporte en un muro.
     */
    public void markReopened() {
        this.status = TicketStatus.OPEN;
    }

    public void close() {
        this.status = TicketStatus.CLOSED;
    }

    public boolean isClosed() {
        return status == TicketStatus.CLOSED;
    }

    /** Si tiene plazo legal y ya venció sin respuesta. Es lo que el panel pinta en rojo. */
    public boolean isOverdue(Instant now) {
        return dueAt != null && status == TicketStatus.OPEN && now.isAfter(dueAt);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UUID getUserId() {
        return userId;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
