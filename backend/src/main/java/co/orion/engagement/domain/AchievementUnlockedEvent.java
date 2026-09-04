package co.orion.engagement.domain;

import java.util.List;
import java.util.UUID;

/**
 * Se encendieron una o varias estrellas. Va la lista completa y no un evento por logro: si alguien
 * enciende tres de golpe, tres notificaciones seguidas se leen como un fallo, no como una
 * celebración.
 */
public record AchievementUnlockedEvent(UUID studentId, List<String> achievementCodes) {
}
