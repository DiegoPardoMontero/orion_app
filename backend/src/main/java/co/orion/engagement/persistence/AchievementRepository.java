package co.orion.engagement.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.engagement.domain.Achievement;

public interface AchievementRepository extends JpaRepository<Achievement, String> {

    List<Achievement> findByActiveTrueOrderByDisplayOrderAsc();
}
