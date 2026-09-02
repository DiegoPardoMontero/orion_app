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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un aviso in-app para un usuario: un mensaje nuevo, una decisión sobre su postulación… El
 * {@code type} es un texto libre acotado (MESSAGE, APPLICATION_APPROVED…) para que sumar tipos
 * sea código y no una migración. {@code linkPath} es la ruta del frontend a la que lleva el aviso.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "title", nullable = false, length = 140)
    private String title;

    @Column(name = "body", length = 400)
    private String body;

    @Column(name = "link_path", length = 200)
    private String linkPath;

    @Column(name = "read_at")
    private Instant readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // exigido por JPA
    }

    public Notification(UUID userId, String type, String title, String body, String linkPath) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.type = Objects.requireNonNull(type, "type");
        this.title = Objects.requireNonNull(title, "title");
        this.body = body;
        this.linkPath = linkPath;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant when) {
        if (this.readAt == null) {
            this.readAt = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
