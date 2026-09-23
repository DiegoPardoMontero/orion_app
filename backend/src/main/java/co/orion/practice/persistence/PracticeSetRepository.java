package co.orion.practice.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.practice.domain.PracticeSet;
import co.orion.practice.domain.PracticeSetStatus;

public interface PracticeSetRepository extends JpaRepository<PracticeSet, UUID> {

    boolean existsByLessonNoteId(UUID lessonNoteId);

    /**
     * Toma un set pendiente para generarlo. Si otra corrida ya lo tiene, no espera: lo salta. Así
     * dos trabajos a la vez nunca generan el mismo set dos veces.
     */
    @Query(value = "select * from practice_sets where id = :id and status = 'PENDING' for update skip locked",
            nativeQuery = true)
    Optional<PracticeSet> reclamarPendiente(@Param("id") UUID id);

    Optional<PracticeSet> findByLessonNoteId(UUID lessonNoteId);

    /** El set que se le ofrece: el más reciente que siga vivo. */
    Optional<PracticeSet> findFirstByStudentIdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID studentId, Collection<PracticeSetStatus> estados, Instant ahora);

    List<PracticeSet> findByStudentIdAndStatusInOrderByCreatedAtDesc(UUID studentId,
                                                                     Collection<PracticeSetStatus> estados);

    List<PracticeSet> findByStatusOrderByCreatedAtAsc(PracticeSetStatus status, Limit limite);

    List<PracticeSet> findByStatusInAndExpiresAtLessThanEqual(Collection<PracticeSetStatus> estados, Instant ahora);

    List<PracticeSet> findByStudentIdAndProfessorIdAndCreatedAtGreaterThanEqual(UUID studentId, UUID professorId,
                                                                                Instant desde);
}
