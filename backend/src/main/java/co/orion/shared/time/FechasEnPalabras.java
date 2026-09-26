package co.orion.shared.time;

import java.text.NumberFormat;
import java.time.Duration;
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

    /** «lunes 14 de septiembre», de un día sin hora: el lunes de una semana, por ejemplo. */
    public static String dia(LocalDate dia) {
        return dia.getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " " + dia.getDayOfMonth() + " de "
                + dia.getMonth().getDisplayName(TextStyle.FULL, ES);
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
        return diaRelativo(instante, ahora) + " a las " + hora(instante);
    }

    /**
     * «de 7:00 a 7:55 PM», o «de 11:30 AM a 12:25 PM» si cruza el mediodía: el meridiano se dice una
     * vez cuando es el mismo, como en la app («7:00 – 7:55 PM»). Una clase se dice siempre con su
     * franja y no con su hora de inicio: sola, una hora parece el comienzo de algo que puede durar
     * media hora (Pardo, 25/09/2026).
     */
    public static String franja(Instant desde, Instant hasta) {
        String inicio = hora(desde);
        String fin = hora(hasta);
        String meridiano = inicio.substring(inicio.length() - 2);
        return fin.endsWith(meridiano)
                ? "de " + inicio.substring(0, inicio.length() - 3) + " a " + fin
                : "de " + inicio + " a " + fin;
    }

    /** «hoy de 7:00 a 7:55 PM», «mañana de …» o «el jueves 26 de septiembre de 7:00 a 7:55 PM». */
    public static String cuandoConFranja(Instant desde, Instant hasta, Instant ahora) {
        return diaRelativo(desde, ahora) + " " + franja(desde, hasta);
    }

    /** «55 minutos». */
    public static String duracion(Instant desde, Instant hasta) {
        long minutos = Duration.between(desde, hasta).toMinutes();
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }

    private static String diaRelativo(Instant instante, Instant ahora) {
        long dias = ChronoUnit.DAYS.between(
                ahora.atZone(BusinessZone.BOGOTA).toLocalDate(), instante.atZone(BusinessZone.BOGOTA).toLocalDate());
        if (dias == 0) {
            return "hoy";
        }
        if (dias == 1) {
            return "mañana";
        }
        return "el " + dia(instante);
    }

    /** «16 de octubre de 2026». */
    public static String fecha(LocalDate dia) {
        return dia.getDayOfMonth() + " de " + dia.getMonth().getDisplayName(TextStyle.FULL, ES) + " de " + dia.getYear();
    }

    /**
     * «$ 180.000», y «−$ 5.000» si es negativo (un ajuste que descuenta): el signo va delante del
     * símbolo, como en la app, y no entre el símbolo y la cifra («$ -5.000»).
     */
    public static String pesos(long cop) {
        String cifra = NumberFormat.getIntegerInstance(ES).format(Math.abs(cop));
        return (cop < 0 ? "−$ " : "$ ") + cifra;
    }

    /** «15 %», o «12,5 %» si no es entero: de puntos básicos (1250) a porcentaje, sin redondear. */
    public static String porcentaje(int bps) {
        return (bps % 100 == 0 ? String.valueOf(bps / 100) : String.valueOf(bps / 100.0).replace('.', ',')) + " %";
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
