package co.orion.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una identidad de Google, Facebook o Apple vinculada a una cuenta de Orión. La llave es el
 * {@code sub} del proveedor, que no cambia; el correo queda solo como constancia.
 */
@Entity
@Table(name = "social_identities")
public class SocialIdentity {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    private SocialProvider provider;

    @Column(name = "subject", nullable = false, length = 255, updatable = false)
    private String subject;

    @Column(name = "email", length = 255, updatable = false)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SocialIdentity() {
    }

    public SocialIdentity(UUID userId, SocialProvider provider, String subject, String email,
                          Instant now) {
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.createdAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public SocialProvider getProvider() {
        return provider;
    }
}
