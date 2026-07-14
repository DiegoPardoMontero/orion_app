package co.orion.scheduling.domain;

import java.time.ZoneId;

/**
 * La zona de negocio, definida una sola vez. Reglas, excepciones y cupos se razonan siempre
 * en hora local de Bogotá (el modelo mental del profesor), aunque se almacenen en UTC.
 */
public final class BusinessZone {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private BusinessZone() {
    }
}
