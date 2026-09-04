package co.orion.engagement.api;

import java.time.ZonedDateTime;

import co.orion.engagement.application.EngagementQueryService;
import co.orion.shared.time.BusinessZone;

/**
 * Un logro con el estado del estudiante. Lleva el progreso aunque no esté encendido: «5 de 8» es
 * justo lo que hace que el siguiente paso parezca alcanzable.
 *
 * <p>{@code glow} y {@code family} son lo que el frontend necesita para dibujar la estrella con un
 * solo componente parametrizado, en vez de sesenta archivos SVG.
 */
public record AchievementResponse(String code,
                                  String family,
                                  String name,
                                  String description,
                                  int progress,
                                  int target,
                                  int glow,
                                  int points,
                                  boolean unlocked,
                                  ZonedDateTime unlockedAt) {

    public static AchievementResponse from(EngagementQueryService.LogroConEstado l) {
        var a = l.achievement();
        return new AchievementResponse(
                a.getCode(),
                a.getFamily().name(),
                a.getName(),
                a.getDescription(),
                l.progress(),
                a.getTarget(),
                a.getGlow(),
                a.getPoints(),
                l.unlocked(),
                l.unlockedAt() == null ? null : l.unlockedAt().atZone(BusinessZone.BOGOTA));
    }
}
