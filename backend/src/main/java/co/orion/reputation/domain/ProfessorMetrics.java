package co.orion.reputation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Agregado de rating de un profesor, materializado. Se recalcula al crear u ocultar una reseña,
 * SOLO sobre las visibles. El id no se genera en la BD: es el professor_id (uno a uno con el
 * usuario), así que se asigna en el constructor.
 *
 * ratingAvg puede ser null (sin reseñas visibles). La regla de exhibición —no mostrar promedio con
 * menos de 3 reseñas— NO vive aquí: esta fila guarda la verdad cruda; el gate lo aplica quien la lee.
 */
@Entity
@Table(name = "professor_metrics")
public class ProfessorMetrics {

    @Id
    @Column(name = "professor_id", updatable = false)
    private UUID professorId;

    @Column(name = "rating_avg")
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected ProfessorMetrics() {
        // exigido por JPA
    }

    public ProfessorMetrics(UUID professorId) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.ratingCount = 0;
    }

    public void recompute(BigDecimal ratingAvg, int ratingCount, Instant computedAt) {
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt");
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
