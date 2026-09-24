package co.orion.shared.text;

/**
 * Texto que escribe una persona y que va dentro de un prompt: en una sola línea y con tope. Una
 * línea porque el prompt lo cita como un dato, y un salto de línea dentro lo haría pasar por una
 * sección más; con tope porque un campo de perfil no es lugar para un ensayo.
 */
public final class TextoParaIa {

    private TextoParaIa() {
    }

    /** {@code null} si no queda nada que decir. */
    public static String unaLinea(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.replaceAll("[\\p{Cntrl}\\s]+", " ").replace('«', '"').replace('»', '"').strip();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo).strip() + "…";
    }
}
