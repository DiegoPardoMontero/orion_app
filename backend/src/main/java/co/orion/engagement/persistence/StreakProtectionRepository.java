package co.orion.engagement.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.engagement.domain.StreakProtection;

public interface StreakProtectionRepository extends JpaRepository<StreakProtection, UUID> {

    List<StreakProtection> findByUserId(UUID userId);

    boolean existsByUserIdAndGrantedFor(UUID userId, LocalDate grantedFor);

    void deleteByUserId(UUID userId);
}
