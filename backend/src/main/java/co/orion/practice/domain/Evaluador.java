package co.orion.practice.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Dice si una respuesta de práctica es correcta. Clase pura, como {@code SlotCalculator}: sin
 * Spring y sin red, para poder probar cada tipo a fondo.
 *
 * <p>Lo que se compara es el contenido, no la forma: mayúsculas, espacios de más y el punto final
 * no hacen incorrecta una respuesta. Y en {@code WRITE_SENTENCE} no hay respuesta exacta: basta con
 * usar el término en una frase de verdad (cuatro palabras o más). <strong>Nunca se marca como
 * incorrecta una frase válida que el generador no previó</strong> (brief, paso B2).
 *
 * <p>Formato de {@code payload} y {@code expected} por tipo:
 * <ul>
 *   <li>FILL_BLANK: {@code {"sentence": "I ___ to…", "options": [...]}}; expected, el término.</li>
 *   <li>FIX_SENTENCE: {@code {"sentence": "I go yesterday", "accepted": [...]}}; expected, la
 *       frase corregida. {@code accepted}, otras correcciones igual de válidas.</li>
 *   <li>MATCH_MEANING: {@code {"terms": [...], "meanings": [...]}}; expected, un objeto JSON
 *       término → significado. La respuesta, el mismo tipo de objeto.</li>
 *   <li>ORDER_DIALOGUE: {@code {"lines": [...desordenadas]}}; expected, el arreglo JSON en orden.
 *       La respuesta, un arreglo JSON.</li>
 *   <li>WRITE_SENTENCE: {@code {"term": "used to"}}; sin expected.</li>
 *   <li>SPOT_ERROR: {@code {"tokens": ["I", "never", "go", ...]}}; expected, {@code {"index": 2,
 *       "correction": "I have never been to Canada."}}. La respuesta, el índice de la ficha tocada.</li>
 *   <li>BUILD_SENTENCE: {@code {"tiles": [...desordenadas], "guide": "¿Dónde está…?"}}; expected, el
 *       arreglo JSON en orden. La respuesta, un arreglo JSON.</li>
 *   <li>CHOOSE_REPLY: {@code {"from": "Receptionist", "message": "…", "options": [...]}}; expected, la
 *       respuesta adecuada.</li>
 *   <li>LISTEN_CHOOSE: {@code {"say": "layover", "options": [...]}}; expected, la opción correcta.</li>
 *   <li>DICTATION: {@code {"say": "Here is my boarding pass."}}; expected, la misma frase. Se perdona
 *       una letra: lo que se practica es oír, no teclear sin errores.</li>
 * </ul>
 */
public final class Evaluador {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PALABRAS_MINIMAS = 4;

    private Evaluador() {
    }

    public static boolean esCorrecta(PracticeItemType tipo, String payload, String expected, String respuesta) {
        if (respuesta == null || respuesta.isBlank()) {
            return false;
        }
        try {
            return switch (tipo) {
                case FILL_BLANK -> normalizar(respuesta).equals(normalizar(expected));
                case FIX_SENTENCE -> aceptadas(payload, expected).contains(normalizar(respuesta));
                case MATCH_MEANING -> pares(JSON.readTree(respuesta)).equals(pares(JSON.readTree(expected)));
                case ORDER_DIALOGUE -> lineas(JSON.readTree(respuesta)).equals(lineas(JSON.readTree(expected)));
                case WRITE_SENTENCE -> usaElTermino(JSON.readTree(payload).path("term").asText(""), respuesta);
                case SPOT_ERROR -> Integer.parseInt(respuesta.strip()) == JSON.readTree(expected).path("index").asInt(-1);
                case BUILD_SENTENCE -> lineas(JSON.readTree(respuesta)).equals(lineas(JSON.readTree(expected)));
                case CHOOSE_REPLY, LISTEN_CHOOSE -> normalizar(respuesta).equals(normalizar(expected));
                case DICTATION -> casiIgual(soloPalabras(respuesta), soloPalabras(expected));
            };
        } catch (Exception ex) {
            // Una respuesta que no se puede leer (un JSON roto desde el cliente) es incorrecta, no un 500.
            return false;
        }
    }

    /** Mayúsculas, tildes de más, espacios y la puntuación de los bordes no cuentan. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = Normalizer.normalize(texto, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replaceAll("\\s+", " ").strip();
        return s.replaceAll("^[\\p{Punct}¿¡\\s]+|[\\p{Punct}\\s]+$", "");
    }

    private static List<String> aceptadas(String payload, String expected) throws Exception {
        List<String> todas = new ArrayList<>();
        todas.add(normalizar(expected));
        for (JsonNode otra : JSON.readTree(payload).path("accepted")) {
            todas.add(normalizar(otra.asText()));
        }
        return todas;
    }

    private static Map<String, String> pares(JsonNode objeto) {
        Map<String, String> pares = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> campos = objeto.fields();
        while (campos.hasNext()) {
            Map.Entry<String, JsonNode> campo = campos.next();
            pares.put(normalizar(campo.getKey()), normalizar(campo.getValue().asText()));
        }
        return pares;
    }

    private static List<String> lineas(JsonNode arreglo) {
        List<String> lineas = new ArrayList<>();
        arreglo.forEach(l -> lineas.add(normalizar(l.asText())));
        return lineas;
    }

    /** El término aparece como tal (no dentro de otra palabra) y la frase es una frase. */
    static boolean usaElTermino(String termino, String frase) {
        if (termino.isBlank()) {
            return false;
        }
        String f = " " + soloPalabras(frase) + " ";
        // El término pasa por la misma limpieza que la frase: «check-in» se busca como «check in», y lo
        // que va entre paréntesis («get used to (+ing)») es una pista para el estudiante, no parte del término.
        String t = soloPalabras(termino.replaceAll("\\([^)]*\\)", " "));
        if (t.isBlank()) {
            return false;
        }
        boolean aparece = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(t) + "(?![\\p{L}])").matcher(f).find();
        long palabras = f.strip().split("\\s+").length;
        return aparece && palabras >= PALABRAS_MINIMAS;
    }

    /** Iguales, o a una sola letra de distancia en frases de diez caracteres o más. */
    static boolean casiIgual(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (b.length() < 10 || Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previa[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cambio = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + cambio);
            }
            int[] t = previa;
            previa = actual;
            actual = t;
        }
        return previa[b.length()] <= 1;
    }

    private static String soloPalabras(String texto) {
        return normalizar(texto).replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }
}
