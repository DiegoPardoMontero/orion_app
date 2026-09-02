package co.orion.catalog.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.catalog.domain.TeachingGoal;

public interface TeachingGoalRepository extends JpaRepository<TeachingGoal, String> {

    List<TeachingGoal> findByActiveTrueOrderByDisplayOrderAsc();
}
