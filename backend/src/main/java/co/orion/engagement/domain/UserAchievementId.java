package co.orion.engagement.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserAchievementId implements Serializable {

    private UUID userId;
    private String achievementCode;

    public UserAchievementId() {
    }

    public UserAchievementId(UUID userId, String achievementCode) {
        this.userId = userId;
        this.achievementCode = achievementCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserAchievementId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(achievementCode, that.achievementCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, achievementCode);
    }
}
