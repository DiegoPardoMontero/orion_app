package co.orion.identity.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Qué profesores suelen tener hueco en cierto día y franja.
 *
 * <p><strong>Por qué es una interfaz y no una llamada directa.</strong> El buscador vive en
 * {@code identity} y la disponibilidad en {@code scheduling}, que ya depende de {@code identity}.
 * Llamar en el otro sentido cerraría un ciclo entre los dos módulos. Así, {@code identity} declara
 * lo que necesita y {@code scheduling} lo implementa: la dependencia sigue yendo en una sola
 * dirección y el buscador no sabe nada del esquema de disponibilidad.
 */
public interface ProfessorAvailabilityLookup {

    /**
     * Ids de los profesores con al menos una franja publicada que deje caber una clase dentro del
     * día y la hora pedidos.
     *
     * @param days  días de la semana pedidos; vacío significa «cualquiera»
     * @param from  hora de inicio del rango, en hora de Bogotá; nulo es «desde que abra»
     * @param to    hora de fin, exclusiva; nulo es «hasta que cierre»
     */
    List<UUID> professorsAvailable(Set<DayOfWeek> days, LocalTime from, LocalTime to);
}
