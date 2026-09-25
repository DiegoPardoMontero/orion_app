package co.orion.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Faltan 14 días para que termine el beneficio de un profe fundador (brief del profe fundador,
 * paso 4). Lo escucha la campana; el correo sale de quien lo publica.
 */
public record FounderEndingEvent(UUID professorId, int founderRateBps, int baseRateBps, Instant until) {
}
