package co.orion.identity.domain;

import java.time.Instant;
import java.util.Locale;
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
 * Invitación a un profesor. Guardamos el HASH del token, no el token en claro (el secreto solo va
 * en el correo). De un solo uso y con caducidad.
 *
 * <p>Desde la V71 no crea la cuenta: guarda el correo, el nombre con el que se saluda al profe, quién
 * lo invita y si trae el beneficio de fundador. El token se consume cuando el invitado crea su cuenta
 * en el registro de profesor, y esa cuenta queda en {@code userId}.
 */
@Entity
@Table(name = "professor_invites")
public class ProfessorInvite {

    /** Lo que ve quien abre el enlace. Un token que no existe se muestra como vencido. */
    public enum State {
        VALID,
        EXPIRED,
        USED
    }

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "professor_name", updatable = false, length = 80)
    private String professorName;

    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    @Column(name = "founder", nullable = false, updatable = false)
    private boolean founder;

    /** La cuenta que se creó con esta invitación; nula hasta que se usa. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected ProfessorInvite() {
    }

    public ProfessorInvite(String email, String professorName, UUID invitedBy, boolean founder,
                           String tokenHash, Instant expiresAt) {
        this.email = Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
        this.professorName = professorName == null || professorName.isBlank() ? null : professorName.trim();
        this.invitedBy = invitedBy;
        this.founder = founder;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public State state(Instant now) {
        if (usedAt != null) {
            return State.USED;
        }
        return expiresAt.isAfter(now) ? State.VALID : State.EXPIRED;
    }

    /** El invitado creó su cuenta con este enlace: queda usado y ligado a esa cuenta. */
    public void consume(UUID newUserId, Instant now) {
        this.userId = Objects.requireNonNull(newUserId, "newUserId");
        this.usedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isFor(String otherEmail) {
        return otherEmail != null && email.equals(otherEmail.trim().toLowerCase(Locale.ROOT));
    }

    public String getEmail() {
        return email;
    }

    public String getProfessorName() {
        return professorName;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public boolean isFounder() {
        return founder;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
