package co.orion.engagement.api;

import co.orion.engagement.application.EngagementQueryService;

/**
 * El resumen de la gamificación del estudiante.
 *
 * <p>{@code sealLevel} es derivado, no almacenado: 1 al registrarse, 2 con dos meses seguidos, 3
 * con medio año. Guardarlo sería una tercera copia de la misma verdad.
 */
public record MyEngagementResponse(long points,
                                   int currentStreakWeeks,
                                   int bestStreakWeeks,
                                   int protectedWeeks,
                                   int sealLevel,
                                   int unlockedCount,
                                   int totalCount) {

    public static MyEngagementResponse from(EngagementQueryService.Resumen r) {
        return new MyEngagementResponse(r.points(), r.currentStreakWeeks(), r.bestStreakWeeks(),
                r.protectedWeeks(), r.sealLevel(), r.unlockedCount(), r.totalCount());
    }
}
