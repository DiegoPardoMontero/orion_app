package co.orion.identity.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.StudentGoal;
import co.orion.identity.domain.StudentGoalId;

public interface StudentGoalRepository extends JpaRepository<StudentGoal, StudentGoalId> {

    List<StudentGoal> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
