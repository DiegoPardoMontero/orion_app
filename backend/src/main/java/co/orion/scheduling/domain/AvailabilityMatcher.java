package co.orion.scheduling.domain;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Si una franja de disponibilidad deja caber una clase dentro del rango que alguien busca.
 *
 * <p>Clase pura, como {@link SlotCalculator}. La regla parece un solapamiento y no lo es: que una
 * franja toque el rango no basta, tiene que caber <em>una clase entera</em>. Una franja de 18:00 a
 * 18:30 solapa con «de 6 a 9 de la noche» y sin embargo nunca va a producir un cupo, porque las
 * clases duran 60 minutos. Un filtro que devuelve profesores con los que no se puede reservar es
 * peor que no tener filtro: manda a la gente a un perfil vacío.
 */
public final class AvailabilityMatcher {

    private AvailabilityMatcher() {
    }

    /**
     * @param from nulo significa «sin límite por abajo»; {@code to} nulo, «sin límite por arriba»
     */
    public static boolean cabeUnaClase(LocalTime inicioFranja, LocalTime finFranja,
                                       LocalTime from, LocalTime to, Duration duracionClase) {
        LocalTime desde = from == null || from.isBefore(inicioFranja) ? inicioFranja : from;
        LocalTime hasta = to == null || to.isAfter(finFranja) ? finFranja : to;

        if (!desde.isBefore(hasta)) {
            return false;
        }
        return Duration.between(desde, hasta).compareTo(duracionClase) >= 0;
    }
}
