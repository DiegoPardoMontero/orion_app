package co.orion.reputation.domain;

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
 * Una sanción a un profesor, con su motivo y su fecha de fin.
 *
 * Toda sanción se le notifica y aparece en su pantalla de desempeño. Una sanción invisible es solo
 * una caída inexplicable de ingresos, y eso no corrige a nadie: lo único que consigue es que la
 * persona se vaya sin entender por qué.
 */
@Entity
@Table(name = "professor_sanctions")
@EntityListeners(AuditingEntityListener.class)
public class ProfessorSanction {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private SanctionType type;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private SanctionState state;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProfessorSanction() {
        // exigido por JPA
    }

    public ProfessorSanction(UUID professorId,
                             SanctionType type,
                             String reason,
                             SanctionState state,
                             Instant startsAt,
                             UUID createdBy) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.type = Objects.requireNonNull(type, "type");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.state = Objects.requireNonNull(state, "state");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.createdBy = createdBy;
        this.endsAt = type.duration() != null ? startsAt.plus(type.duration()) : null;

        if (type == SanctionType.ACCOUNT_SUSPENDED && createdBy == null) {
            throw new IllegalArgumentException("Cerrar una cuenta es siempre una decisión de una persona");
        }
    }

    /** ¿Está surtiendo efecto ahora mismo? Propuesta no es activa; vencida tampoco. */
    public boolean isActiveAt(Instant now) {
        return state == SanctionState.ACTIVE
                && !startsAt.isAfter(now)
                && (endsAt == null || endsAt.isAfter(now));
    }

    public boolean isProposed() {
        return state == SanctionState.PROPOSED;
    }

    /** El admin confirma una propuesta del sistema: a partir de aquí sí tiene consecuencias. */
    public void confirm(UUID adminId, Instant now) {
        if (state != SanctionState.PROPOSED) {
            throw new IllegalStateException("Solo una sanción propuesta se puede confirmar");
        }
        this.state = SanctionState.ACTIVE;
        this.startsAt = now;
        this.endsAt = type.duration() != null ? now.plus(type.duration()) : null;
        this.createdBy = adminId;
    }

    /** Levantarla. Se conserva la fila: el historial no se borra, se marca. */
    public void revoke(UUID adminId, Instant now) {
        if (state == SanctionState.REVOKED) {
            throw new IllegalStateException("Esta sanción ya estaba levantada");
        }
        this.state = SanctionState.REVOKED;
        this.revokedBy = Objects.requireNonNull(adminId, "adminId");
        this.revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public SanctionType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public SanctionState getState() {
        return state;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
