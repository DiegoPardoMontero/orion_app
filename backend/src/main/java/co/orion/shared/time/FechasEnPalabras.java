package co.orion.shared.time;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Fechas y horas como se dicen en un mensaje o un aviso, en hora de Bogotá: «jueves 26 de
 * septiembre», «7:00 PM». La hora va como en el resto de la app (AM/PM y no «p. m.»).
 */
public final class FechasEnPalabras {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private FechasEnPalabras() {
    }

    /** «jueves 26 de septiembre». */
    public static String dia(Instant instante) {
        ZonedDateTime z = instante.atZone(BusinessZone.BOGOTA);
        return z.getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " " + z.getDayOfMonth() + " de "
                + z.getMonth().getDisplayName(TextStyle.FULL, ES);
    }

    /** «jue 26 sep». */
    public static String diaCorto(Instant instante) {
        ZonedDateTime z = instante.atZone(BusinessZone.BOGOTA);
        String semana = z.getDayOfWeek().getDisplayName(TextStyle.SHORT, ES).replace(".", "");
        String mes = z.getMonth().getDisplayName(TextStyle.SHORT, ES).replace(".", "");
        return semana + " " + z.getDayOfMonth() + " " + (mes.length() > 3 ? mes.substring(0, 3) : mes);
    }

    /** «7:00 PM». */
    public static String hora(Instant instante) {
        ZonedDateTime z = instante.atZone(BusinessZone.BOGOTA);
        int h = z.getHour() % 12 == 0 ? 12 : z.getHour() % 12;
        return h + ":" + String.format("%02d", z.getMinute()) + (z.getHour() < 12 ? " AM" : " PM");
    }

    /** «hoy a las 7:00 PM», «mañana a las 7:00 PM» o «el jueves 26 de septiembre a las 7:00 PM». */
    public static String cuando(Instant instante, Instant ahora) {
        long dias = ChronoUnit.DAYS.between(
                ahora.atZone(BusinessZone.BOGOTA).toLocalDate(), instante.atZone(BusinessZone.BOGOTA).toLocalDate());
        String a = " a las " + hora(instante);
        if (dias == 0) {
            return "hoy" + a;
        }
        if (dias == 1) {
            return "mañana" + a;
        }
        return "el " + dia(instante) + a;
    }

    /** «$ 180.000». */
    public static String pesos(long cop) {
        return "$ " + NumberFormat.getIntegerInstance(ES).format(cop);
    }

    /** «1 al 15 de septiembre», o «28 de agosto al 3 de septiembre» si cambia de mes. */
    public static String periodo(LocalDate desde, LocalDate hasta) {
        String mesHasta = hasta.getMonth().getDisplayName(TextStyle.FULL, ES);
        if (desde.getMonth() == hasta.getMonth()) {
            return desde.getDayOfMonth() + " al " + hasta.getDayOfMonth() + " de " + mesHasta;
        }
        return desde.getDayOfMonth() + " de " + desde.getMonth().getDisplayName(TextStyle.FULL, ES) + " al "
                + hasta.getDayOfMonth() + " de " + mesHasta;
    }

    /** El primer nombre: «María» de «María Gómez». */
    public static String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "";
        }
        return nombreCompleto.trim().split("\\s+")[0];
    }
}
