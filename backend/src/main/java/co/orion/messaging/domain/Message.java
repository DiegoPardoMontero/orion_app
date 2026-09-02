package co.orion.messaging.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un mensaje dentro de una conversación. {@code body} es lo que ven las partes (posiblemente
 * enmascarado por la política de contacto); {@code bodyOriginal} guarda el texto tal cual lo
 * escribió el autor cuando hubo enmascarado, para moderación. Un mensaje del sistema tiene
 * {@code senderId} nulo e {@code isSystem = true}.
 */
@Entity
@Table(name = "messages")
@EntityListeners(AuditingEntityListener.class)
public class Message {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    /** Quién lo escribió. Null en los mensajes del sistema. */
    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    @Column(name = "body", nullable = false)
    private String body;

    /** El texto original antes del enmascarado. Null cuando no hubo nada que ocultar. */
    @Column(name = "body_original")
    private String bodyOriginal;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Enumerated(EnumType.STRING)
    @Column(name = "flagged_reason", length = 40)
    private FlaggedReason flaggedReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** Marca de idempotencia: cuándo se disparó la notificación/correo de este mensaje. */
    @Column(name = "notified_at")
    private Instant notifiedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
        // exigido por JPA
    }

    /** Mensaje de una persona. Si hubo enmascarado, {@code bodyOriginal} y {@code flaggedReason} lo registran. */
    public Message(UUID conversationId, UUID senderId, String body, String bodyOriginal, FlaggedReason flaggedReason) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.body = Objects.requireNonNull(body, "body");
        this.bodyOriginal = bodyOriginal;
        this.flaggedReason = flaggedReason;
        this.system = false;
    }

    /** Mensaje del sistema: sin autor, nunca enmascarado. */
    public static Message system(UUID conversationId, String body) {
        Message message = new Message();
        message.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        message.body = Objects.requireNonNull(body, "body");
        message.system = true;
        return message;
    }

    public boolean isFlagged() {
        return flaggedReason != null;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant when) {
        if (this.readAt == null) {
            this.readAt = when;
        }
    }

    public void markNotified(Instant when) {
        this.notifiedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public boolean isSystem() {
        return system;
    }

    public String getBody() {
        return body;
    }

    public String getBodyOriginal() {
        return bodyOriginal;
    }

    public FlaggedReason getFlaggedReason() {
        return flaggedReason;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
