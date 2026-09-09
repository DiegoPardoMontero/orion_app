package co.orion.shared.time;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;


/**
 * Cuándo vence una solicitud. Clase pura: sin Spring, sin repositorios, con el «ahora» por
 * parámetro — el mismo trato que {@code SlotCalculator}, y por la misma razón.
 *
 * <p>Los plazos no son una política nuestra:
 * <ul>
 *   <li><strong>10 días hábiles</strong> para una consulta (art. 14 de la Ley 1581 de 2012).</li>
 *   <li><strong>15 días hábiles</strong> para un reclamo (art. 15 de la Ley 1581 de 2012).</li>
 *   <li><strong>15 días calendario</strong> para devolver el dinero de un retracto (art. 47 de la
 *       Ley 1480 de 2011, en el plazo que fijó la Ley 2439 de 2024).</li>
 * </ul>
 *
 * <p><strong>Los días hábiles aquí son de lunes a viernes y no descuentan festivos.</strong> Es
 * deliberado. Descontarlos exigiría mantener el calendario de festivos de Colombia —que la Ley 51
 * de 1983 mueve al lunes siguiente, así que no basta con una lista de fechas fijas— y equivocarse
 * en un año haría vencer un plazo legal por sorpresa. Al no descontarlos, la fecha que calculamos
 * llega <em>antes</em> que la legal: nos exige contestar más pronto, nunca más tarde. Errar hacia
 * el lado de responder antes es gratis; errar hacia el otro lo sanciona la SIC.
 */
public final class PlazoLegal {

    private PlazoLegal() {
    }

    /**
     * Suma días hábiles al momento dado y devuelve el final de ese día en Bogotá.
     *
     * <p>Vence al cierre del último día hábil y no a la misma hora en que llegó: un reclamo
     * presentado a las 11 de la noche no da un día menos que uno presentado por la mañana.
     */
    public static Instant sumandoDiasHabiles(Instant desde, int dias) {
        LocalDate fecha = LocalDate.ofInstant(desde, BusinessZone.BOGOTA);
        int sumados = 0;
        while (sumados < dias) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                sumados++;
            }
        }
        return finDelDia(fecha);
    }

    /** Días calendario: aquí sí cuentan sábados, domingos y festivos. */
    public static Instant sumandoDiasCalendario(Instant desde, int dias) {
        LocalDate fecha = LocalDate.ofInstant(desde, BusinessZone.BOGOTA).plusDays(dias);
        return finDelDia(fecha);
    }

    private static boolean esHabil(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }

    private static Instant finDelDia(LocalDate fecha) {
        return ZonedDateTime.of(fecha, LocalTime.MAX, BusinessZone.BOGOTA).toInstant();
    }
}
