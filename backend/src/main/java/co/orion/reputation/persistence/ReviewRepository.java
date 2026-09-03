package co.orion.reputation.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.reputation.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingId(UUID bookingId);

    /** Las reseñas visibles de un profesor, de la más nueva a la más vieja (listado público). */
    Page<Review> findByProfessorIdAndVisibleTrueOrderByCreatedAtDesc(UUID professorId, Pageable pageable);

    /** La cola de moderación: reseñas reportadas que aún están visibles, la reportada primero. */
    List<Review> findByReportedAtNotNullAndVisibleTrueOrderByReportedAtDesc();

    /**
     * El agregado sobre las reseñas VISIBLES de un profesor: promedio y conteo en una sola consulta.
     * avg() devuelve null cuando no hay filas; count() devuelve 0. La proyección lo deja explícito.
     */
    @Query("""
            select avg(r.rating) as average, count(r) as total
            from Review r
            where r.professorId = :professorId
              and r.visible = true
            """)
    RatingAggregate aggregateVisible(@Param("professorId") UUID professorId);

    interface RatingAggregate {
        Double getAverage();

        long getTotal();
    }

    /** Reseñas que un profesor reportó y el admin todavía no ha revisado. */
    @org.springframework.data.jpa.repository.Query("""
            select count(r) from Review r where r.reportedAt is not null and r.visible = true
            """)
    long countReported();
}
