package co.orion.engagement.api;

import co.orion.engagement.application.EngagementQueryService;

/**
 * Una pieza del avatar con su estado.
 *
 * <p>{@code unlockCondition} va en TEXTO y no como código de logro: el estudiante tiene que leer
 * «Con diez clases», no {@code volumen-10-clases}. Sale de la descripción del catálogo, que ya
 * está redactada con la voz de marca.
 */
public record CosmeticResponse(String kind,
                               String code,
                               String name,
                               String zone,
                               boolean unlocked,
                               String unlockCondition,
                               boolean equipped) {

    public static CosmeticResponse from(EngagementQueryService.CosmeticoConEstado c) {
        var pieza = c.cosmetic();
        return new CosmeticResponse(
                pieza.getKind().name(),
                pieza.getCode(),
                pieza.getName(),
                pieza.getZone(),
                c.unlocked(),
                c.unlockCondition(),
                c.equipped());
    }
}
