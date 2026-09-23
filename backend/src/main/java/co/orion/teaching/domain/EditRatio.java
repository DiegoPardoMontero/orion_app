package co.orion.teaching.domain;

/**
 * Cuánto cambió el profesor el borrador de la IA antes de publicarlo: 0 si lo publicó tal cual, 1
 * si lo reescribió entero.
 *
 * <p>Es el termómetro de la función (brief, paso A1): si más de la mitad de las actas se
 * reescriben, el prompt estorba en vez de ayudar, y eso tiene que verse en una cifra y no en una
 * corazonada. Distancia de Levenshtein sobre el texto normalizado, dividida por el largo del más
 * largo. Clase pura, como {@code SlotCalculator}.
 */
public final class EditRatio {

    /** Por encima de esto no se calcula exacto: dos textos de 5.000 caracteres ya no caben en memoria barata. */
    private static final int LARGO_MAXIMO = 5000;

    private EditRatio() {
    }

    public static double entre(String original, String publicado) {
        String a = normalizar(original);
        String b = normalizar(publicado);
        if (a.equals(b)) {
            return 0.0;
        }
        int largo = Math.max(a.length(), b.length());
        if (largo == 0) {
            return 0.0;
        }
        a = a.length() > LARGO_MAXIMO ? a.substring(0, LARGO_MAXIMO) : a;
        b = b.length() > LARGO_MAXIMO ? b.substring(0, LARGO_MAXIMO) : b;
        double ratio = (double) levenshtein(a, b) / Math.max(a.length(), b.length());
        return Math.round(Math.min(1.0, ratio) * 1000) / 1000.0;
    }

    /** Espacios de más y mayúsculas no son una edición: nadie «reescribió» por cambiar un espacio. */
    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static int levenshtein(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previa[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + costo);
            }
            int[] t = previa;
            previa = actual;
            actual = t;
        }
        return previa[b.length()];
    }
}
