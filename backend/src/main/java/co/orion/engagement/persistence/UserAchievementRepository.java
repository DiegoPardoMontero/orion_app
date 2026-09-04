package co.orion.engagement.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.engagement.domain.UserAchievement;
import co.orion.engagement.domain.UserAchievementId;

public interface UserAchievementRepository
        extends JpaRepository<UserAchievement, UserAchievementId> {

    List<UserAchievement> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
