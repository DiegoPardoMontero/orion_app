package co.orion.scheduling.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.scheduling.domain.ProfessorAbsence;

public interface ProfessorAbsenceRepository extends JpaRepository<ProfessorAbsence, UUID> {

    boolean existsByBookingId(UUID bookingId);

    /** Las ausencias de un profesor dentro de la ventana móvil: el insumo de las sanciones. */
    List<ProfessorAbsence> findByProfessorIdAndOccurredAtAfterOrderByOccurredAtDesc(
            UUID professorId, Instant since);

    long countByProfessorIdAndOccurredAtAfter(UUID professorId, Instant since);
}
