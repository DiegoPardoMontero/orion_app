package co.orion.assessment.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

/**
 * Un profesor recomendado en un diagnóstico, ya persistido.
 *
 * <p>{@code bookedAt} es la métrica que dice si esta función le sirve al negocio: qué diagnósticos
 * terminan en reserva, y con cuál de las tres posiciones. Sin eso, el diagnóstico es una función
 * bonita de la que nadie sabe si vende.
 */
@Entity
@Table(name = "assessment_recommendations")
public class AssessmentRecommendation {

    @EmbeddedId
    private AssessmentRecommendationId id;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "reason_text", nullable = false, length = 240)
    private String reasonText;

    @Column(name = "booked_at")
    private Instant bookedAt;

    protected AssessmentRecommendation() {
    }

    public AssessmentRecommendation(UUID assessmentId, Recomendacion fuente) {
        this.id = new AssessmentRecommendationId(assessmentId, fuente.professorId());
        this.position = (short) fuente.position();
        this.reasonCode = fuente.reasonCode().name();
        this.reasonText = fuente.reasonText();
    }

    public void markBooked(Instant now) {
        this.bookedAt = now;
    }

    public UUID getAssessmentId() {
        return id.getAssessmentId();
    }

    public UUID getProfessorId() {
        return id.getProfessorId();
    }

    public short getPosition() {
        return position;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }
}
