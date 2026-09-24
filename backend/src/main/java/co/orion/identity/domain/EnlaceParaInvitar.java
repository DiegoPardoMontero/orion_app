package co.orion.identity.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * El nombre corto del enlace para invitar: «María José Gómez» → «maria-jose-gomez». Clase pura: sin
 * tildes ni eñes (la ñ pasa a n), solo letras y números separados por guiones, y a lo sumo 40
 * caracteres cortados en un guion. Quién se queda con él cuando dos se llaman igual lo decide el
 * servicio, que añade «-2», «-3»…
 */
public final class EnlaceParaInvitar {

    static final int MAXIMO = 40;

    private EnlaceParaInvitar() {
    }

    public static String slugDe(String nombre) {
        String sinTildes = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String s = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > MAXIMO) {
            s = s.substring(0, MAXIMO);
            int guion = s.lastIndexOf('-');
            if (guion > 10) {
                s = s.substring(0, guion);
            }
            s = s.replaceAll("-+$", "");
        }
        return s.isEmpty() ? "profe" : s;
    }
}
