package co.orion.support.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un mensaje del hilo. No se edita ni se borra: un registro que se puede reescribir no registra. */
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    public static final int MAX_CUERPO = 4000;

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected SupportMessage() {
        // exigido por JPA
    }

    public SupportMessage(UUID ticketId, UUID authorId, String body) {
        this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.body = requireCuerpo(body);
    }

    private static String requireCuerpo(String body) {
        String limpio = body == null ? "" : body.trim();
        if (limpio.isEmpty()) {
            throw new UnprocessableException("El mensaje no puede ir vacío.");
        }
        if (limpio.length() > MAX_CUERPO) {
            throw new UnprocessableException(
                    "El mensaje no puede pasar de " + MAX_CUERPO + " caracteres.");
        }
        return limpio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
