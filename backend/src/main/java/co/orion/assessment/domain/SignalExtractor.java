package co.orion.assessment.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deduce de la transcripción las señales que el cliente no puede medir.
 *
 * <p>La latencia y la duración las mide el navegador, que es el único que ve el audio. Todo lo
 * demás sale de aquí, y sale <strong>en el servidor</strong> a propósito: un puntaje que dependiera
 * de números enviados por el cliente no sería reproducible, y cualquiera podría regalarse un cien.
 *
 * <p>Clase pura, como {@link ConfidenceScoreCalculator}: sin Spring y sin reloj. El mismo texto da
 * siempre las mismas señales, que es lo mínimo que se le puede pedir a algo que sostiene una marca
 * registrada.
 *
 * <p><strong>Honestidad sobre lo que esto es.</strong> Son heurísticas sobre texto, no análisis
 * lingüístico. Reconocen los marcadores que aparecen en la conversación real que tenemos grabada —
 * «I mean», la palabra repetida, la frase que se corta en una preposición— y se equivocarán en
 * casos que todavía no hemos visto. Por eso las cinco dimensiones usan medianas y no promedios: un
 * turno mal leído no puede definir un diagnóstico. Con más grabaciones esto se afina; hoy es lo
 * mejor que se puede sostener con una.
 */
public final class SignalExtractor {

    private SignalExtractor() {
    }

    /** Muletillas: relleno mientras se busca la palabra. No cuentan como error, cuentan como esfuerzo. */
    private static final Set<String> MULETILLAS = Set.of(
            "um", "uh", "uhm", "erm", "eh", "ehm", "mmm", "hmm", "mm",
            "like", "so", "well", "okay", "ok", "right", "yeah",
            "este", "osea", "bueno", "pues");

    /** Marcadores de reparación: la persona se corrige a mitad de frase. */
    private static final Pattern REPARACION = Pattern.compile(
            "\\b(i mean|sorry|no wait|actually|or rather|perdon|digo|mejor dicho)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Palabra repetida seguida: «the the», «I I». Es el tartamudeo de quien va rearmando. */
    private static final Pattern REPETICION = Pattern.compile("\\b(\\w+)\\s+\\1\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Palabras con las que nadie termina una frase. Si el turno acaba aquí, la frase se abandonó:
     * se empezó a construir algo y se soltó a medias.
     */
    private static final Set<String> NO_TERMINAN_FRASE = Set.of(
            "and", "but", "or", "because", "so", "that", "which", "if", "when", "to", "of", "for",
            "with", "in", "on", "at", "the", "a", "an", "my", "your", "is", "was", "i", "it");

    /** Palabras funcionales del español. Su presencia en un turno delata el cambio de idioma. */
    private static final Set<String> ESPANOL = Set.of(
            "que", "como", "pero", "porque", "para", "con", "una", "uno", "los", "las", "del",
            "muy", "mas", "este", "esta", "eso", "esto", "yo", "tu", "usted", "nosotros",
            "entonces", "tambien", "aqui", "alli", "ahora", "siempre", "nunca", "puedo", "quiero",
            "tengo", "hablar", "gracias", "disculpa", "perdon", "espanol", "ingles", "no", "si");

    /** Cuántas palabras dijo. Es la base de la dimensión de extensión. */
    public static int wordCount(String transcripcion) {
        return palabras(transcripcion).size();
    }

    /** Veces que se corrigió a mitad de frase. */
    public static int selfCorrections(String transcripcion) {
        if (vacio(transcripcion)) {
            return 0;
        }
        int marcadores = contar(REPARACION, transcripcion);
        int repeticiones = contar(REPETICION, transcripcion);
        return marcadores + repeticiones;
    }

    /**
     * Frases que empezó y soltó.
     *
     * <p>Dos formas de contarlo: los puntos suspensivos y los guiones que deja la transcripción
     * cuando alguien se corta, y el turno que termina en una palabra con la que no se acaba una
     * frase. Lo segundo es lo que de verdad pasa al hablar un idioma que no dominas: se construye
     * una oración, se llega a la preposición, y no aparece lo que iba después.
     */
    public static int abandonedClauses(String transcripcion) {
        if (vacio(transcripcion)) {
            return 0;
        }
        int cortes = contar(Pattern.compile("\\.\\.\\.|—|--"), transcripcion);

        List<String> palabras = palabras(transcripcion);
        boolean terminaColgando = !palabras.isEmpty()
                && NO_TERMINAN_FRASE.contains(normalizar(palabras.getLast()))
                && !transcripcion.trim().endsWith("?");

        return cortes + (terminaColgando ? 1 : 0);
    }

    /** Muletillas del turno. */
    public static int fillerCount(String transcripcion) {
        return (int) palabras(transcripcion).stream()
                .map(SignalExtractor::normalizar)
                .filter(MULETILLAS::contains)
                .count();
    }

    /**
     * Si la persona se fue a su idioma en este turno.
     *
     * <p>Solo aplica cuando el idioma evaluado no es el español: si la clase es de español, hablar
     * español no es rendirse, es responder. La señal se apoya en dos cosas —caracteres que el inglés
     * no tiene y palabras funcionales del español—, y exige más de una para no marcar a quien dijo
     * «no» o el nombre de su ciudad.
     */
    public static boolean nativeSwitch(String transcripcion, String idiomaEvaluado) {
        if (vacio(transcripcion) || "ES".equalsIgnoreCase(idiomaEvaluado)) {
            return false;
        }
        if (Pattern.compile("[ñáéíóúü¿¡]", Pattern.CASE_INSENSITIVE).matcher(transcripcion).find()) {
            return true;
        }
        long funcionales = palabras(transcripcion).stream()
                .map(SignalExtractor::normalizar)
                .filter(ESPANOL::contains)
                .count();
        return funcionales >= 2;
    }

    private static List<String> palabras(String transcripcion) {
        if (vacio(transcripcion)) {
            return List.of();
        }
        return List.of(transcripcion.trim().split("\\s+"));
    }

    private static int contar(Pattern patron, String texto) {
        var matcher = patron.matcher(texto);
        int veces = 0;
        while (matcher.find()) {
            veces++;
        }
        return veces;
    }

    /** Minúsculas, sin tildes y sin puntuación: «Entonces,» y «entonces» son la misma palabra. */
    private static String normalizar(String palabra) {
        String sinTildes = Normalizer.normalize(palabra, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
