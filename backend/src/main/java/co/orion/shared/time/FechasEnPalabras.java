package co.orion.shared.time;

import java.time.Instant;
import java.time.ZonedDateTime;
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

    /** El primer nombre: «María» de «María Gómez». */
    public static String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "";
        }
        return nombreCompleto.trim().split("\\s+")[0];
    }
}
