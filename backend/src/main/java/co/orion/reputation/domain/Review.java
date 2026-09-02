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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * La reseña que un estudiante deja sobre una clase que ya ocurrió. Como en el resto de módulos, las
 * referencias a usuarios y a la reserva son UUIDs planos, no relaciones JPA: la integridad la
 * garantizan las FK (y el UNIQUE sobre booking_id, que cierra la doble reseña), no el grafo.
 *
 * La fila NUNCA se borra. El profesor puede REPORTARLA (reported_at/reason) y el admin OCULTARLA
 * (is_visible=false + hidden_reason): ambas acciones dejan rastro. Solo las visibles cuentan en el
 * agregado de rating.
 */
@Entity
@Table(name = "reviews")
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "is_visible", nullable = false)
    private boolean visible;

    @Column(name = "hidden_by")
    private UUID hiddenBy;

    @Column(name = "hidden_reason", length = 300)
    private String hiddenReason;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "reported_reason", length = 300)
    private String reportedReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
        // exigido por JPA
    }

    public Review(UUID bookingId, UUID studentId, UUID professorId, short rating, String comment) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.rating = rating;
        this.comment = comment;
        this.visible = true;
    }

    /** El profesor marca la reseña para revisión. No la oculta: solo el admin decide eso. */
    public void report(String reason, Instant at) {
        this.reportedAt = Objects.requireNonNull(at, "at");
        this.reportedReason = reason;
    }

    /** El admin la retira del agregado y del listado público. La fila sigue existiendo. */
    public void hide(UUID adminId, String reason) {
        this.visible = false;
        this.hiddenBy = Objects.requireNonNull(adminId, "adminId");
        this.hiddenReason = reason;
    }

    public boolean isReported() {
        return reportedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public short getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public boolean isVisible() {
        return visible;
    }

    public UUID getHiddenBy() {
        return hiddenBy;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public String getReportedReason() {
        return reportedReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
