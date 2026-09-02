package co.orion.identity.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorGoalId;

public interface ProfessorGoalRepository extends JpaRepository<ProfessorGoal, ProfessorGoalId> {

    List<ProfessorGoal> findByProfessorId(UUID professorId);

    List<ProfessorGoal> findByProfessorIdIn(Collection<UUID> professorIds);

    @Modifying
    @Query("delete from ProfessorGoal g where g.professorId = :professorId")
    void deleteByProfessorId(@Param("professorId") UUID professorId);
}
