package co.orion.identity.application;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import co.orion.shared.time.BusinessZone;

/**
 * El aviso de que el beneficio de fundador termina, en las mismas palabras para la campana y el
 * correo (brief del profe fundador, paso 4).
 */
public final class FinDelFundador {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private FinDelFundador() {
    }

    public static final String TITULO = "Tu beneficio de profe fundador termina pronto";

    /** «12 de enero de 2027»: el día, en Bogotá, desde el que las reservas nuevas llevan la base. */
    public static String fecha(Instant until) {
        return FECHA.format(until.atZone(BusinessZone.BOGOTA));
    }

    public static String texto(int founderRateBps, int baseRateBps, Instant until) {
        String fundador = porcentaje(founderRateBps);
        return "Tu comisión de profe fundador (" + fundador + ") termina el " + fecha(until) + ". Desde ese día, "
                + "las reservas nuevas llevan la comisión estándar de Orión, " + porcentaje(baseRateBps) + ". "
                + "Las reservas que ya tengas conservan el " + fundador + ".";
    }

    private static String porcentaje(int bps) {
        return (bps % 100 == 0 ? String.valueOf(bps / 100) : String.valueOf(bps / 100.0).replace('.', ',')) + " %";
    }
}
