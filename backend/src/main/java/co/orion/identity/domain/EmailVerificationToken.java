package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Token de verificación de correo. Mismo trato que {@link PasswordResetToken}: en la base vive el
 * hash, el secreto solo viaja en el correo, es de un solo uso y caduca.
 *
 * <p>Guarda además la dirección a la que se envió. Si la persona cambia de correo antes de
 * verificar, el token deja de valer — verificaría una dirección que ya nadie usa, y eso es
 * exactamente el agujero que esta tabla existe para cerrar.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "email", nullable = false, updatable = false, length = 255)
    private String email;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected EmailVerificationToken() {
        // exigido por JPA
    }

    public EmailVerificationToken(UUID userId, String tokenHash, String email, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.email = Objects.requireNonNull(email, "email");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = Objects.requireNonNull(now, "now");
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
