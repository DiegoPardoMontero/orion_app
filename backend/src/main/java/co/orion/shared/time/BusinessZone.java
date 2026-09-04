package co.orion.shared.time;

import java.time.ZoneId;

/**
 * La zona de negocio, definida una sola vez. Reglas, excepciones, cupos, rachas y cortes de mes se
 * razonan siempre en hora local de Bogotá (el modelo mental del profesor), aunque se almacenen en
 * UTC.
 *
 * <p>Vive en {@code shared} y no en {@code scheduling} porque siete de los diez módulos la
 * necesitan, y hacerles importar {@code scheduling} para leer una zona horaria creaba dependencias
 * que no significaban nada — y en el caso de {@code identity}, un ciclo.
 */
public final class BusinessZone {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private BusinessZone() {
    }
}
