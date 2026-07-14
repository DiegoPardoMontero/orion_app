package co.orion.scheduling.domain;

import java.time.ZonedDateTime;

/**
 * Franja ya ocupada, [start, end). En la Tarea 3 se llenará con las reservas confirmadas;
 * por ahora el calculador ya sabe restarla y los tests la ejercitan con datos sintéticos.
 */
public record OccupiedInterval(ZonedDateTime start, ZonedDateTime end) {
}
