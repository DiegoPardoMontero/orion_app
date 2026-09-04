package co.orion.engagement.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Dónde va un estudiante en un logro. Guarda el progreso aunque no lo haya alcanzado, porque
 * «5 de 8» es justo lo que hace que el siguiente paso parezca alcanzable.
 */
@Entity
@Table(name = "user_achievements")
@IdClass(UserAchievementId.class)
public class UserAchievement {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "achievement_code", length = 60)
    private String achievementCode;

    @Column(name = "progress", nullable = false)
    private int progress;

    /** Nulo mientras no esté encendida. Es lo que distingue «va por 5 de 8» de «ya está». */
    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    protected UserAchievement() {
        // exigido por JPA
    }

    public UserAchievement(UUID userId, String achievementCode) {
        this.userId = userId;
        this.achievementCode = achievementCode;
    }

    /**
     * Guarda el avance. El progreso <strong>no baja</strong>: una racha que se corta no apaga la
     * estrella que ya se encendió, y ver un número retroceder es lo contrario de motivar.
     */
    public void recordProgress(int nuevo) {
        this.progress = Math.max(this.progress, nuevo);
    }

    /** Enciende la estrella. Devuelve false si ya lo estaba: es lo que evita el doble desbloqueo. */
    public boolean unlock(Instant when) {
        if (unlockedAt != null) {
            return false;
        }
        this.unlockedAt = when;
        return true;
    }

    public boolean isUnlocked() {
        return unlockedAt != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAchievementCode() {
        return achievementCode;
    }

    public int getProgress() {
        return progress;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
