package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.orion.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * La postulación de una persona a profesor. La máquina de estados vive aquí: cada transición valida
 * su estado origen y lanza {@link ConflictException} (→ 409) si no cuadra. Nada de setStatus público:
 * el estado solo cambia por una transición con nombre (submit, startReview, approve, reject...).
 *
 * Como el resto de identity, la referencia al usuario es un UUID plano, no una relación JPA.
 */
@Entity
@Table(name = "teacher_applications")
@EntityListeners(AuditingEntityListener.class)
public class TeacherApplication {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private ApplicationStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TeacherApplication() {
        // exigido por JPA
    }

    public TeacherApplication(UUID userId) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = ApplicationStatus.DRAFT;
    }

    /** Alta directa en un estado dado (para la invitación del admin: nace APPROVED). */
    public TeacherApplication(UUID userId, ApplicationStatus status, UUID reviewedBy, Instant reviewedAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public void submit(Instant now) {
        require(ApplicationStatus.DRAFT, "enviar");
        this.status = ApplicationStatus.PENDING_REVIEW;
        this.submittedAt = now;
    }

    public void markResubmitted(Instant now) {
        require(ApplicationStatus.CHANGES_REQUESTED, "reenviar");
        this.status = ApplicationStatus.PENDING_REVIEW;
        this.submittedAt = now;
    }

    public void startReview() {
        require(ApplicationStatus.PENDING_REVIEW, "iniciar revisión");
        this.status = ApplicationStatus.UNDER_REVIEW;
    }

    public void approve(UUID reviewerId, String note, Instant now) {
        require(ApplicationStatus.UNDER_REVIEW, "aprobar");
        this.status = ApplicationStatus.APPROVED;
        decide(reviewerId, note, now);
    }

    public void reject(UUID reviewerId, String note, Instant now) {
        require(ApplicationStatus.UNDER_REVIEW, "rechazar");
        this.status = ApplicationStatus.REJECTED;
        decide(reviewerId, note, now);
    }

    public void requestChanges(UUID reviewerId, String note, Instant now) {
        require(ApplicationStatus.UNDER_REVIEW, "pedir cambios");
        this.status = ApplicationStatus.CHANGES_REQUESTED;
        decide(reviewerId, note, now);
    }

    private void decide(UUID reviewerId, String note, Instant now) {
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.decisionNote = note;
    }

    private void require(ApplicationStatus expected, String action) {
        if (this.status != expected) {
            throw new ConflictException(
                    "No se puede " + action + " una postulación en estado " + this.status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
