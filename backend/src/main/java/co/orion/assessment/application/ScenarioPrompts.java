package co.orion.assessment.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Carga el guion de la conversación desde {@code resources/prompts/}.
 *
 * <p>No se incrusta en el código a propósito: se ajusta sin recompilar, y el nombre del archivo
 * junto con {@code score_version} dejan dicho qué versión produjo qué diagnóstico. Las líneas que
 * empiezan por {@code #} son notas para nosotros y no viajan al modelo — son la mitad del valor del
 * archivo y no tienen por qué gastar tokens ni confundir a la IA.
 *
 * <p>Se lee en cada apertura y no se cachea: una conversación al día no justifica una caché, y sí
 * justifica poder corregir una frase del guion y ver el efecto en la siguiente.
 */
@Component
public class ScenarioPrompts {

    /** El guion vigente. Subir de versión es cambiar esta constante y dejar el archivo viejo. */
    static final String VIGENTE = "prompts/assessment-scenario-v3.txt";

    private static final Map<String, String> NOMBRE_DEL_IDIOMA = Map.of(
            "EN", "inglés",
            "FR", "francés",
            "ES", "español");

    public String escenario(String languageCode, int minutos, String nombreDePila) {
        return sinComentarios(leer(VIGENTE))
                .replace("{{NOMBRE}}", nombreDePila == null || nombreDePila.isBlank()
                        ? "la persona" : nombreDePila)
                .replace("{{IDIOMA}}", NOMBRE_DEL_IDIOMA.getOrDefault(
                        languageCode == null ? "EN" : languageCode.toUpperCase(), "inglés"))
                .replace("{{MINUTOS}}", String.valueOf(minutos));
    }

    /** Qué guion se usó. Va al registro, para poder explicar un diagnóstico de hace seis meses. */
    public String versionVigente() {
        return VIGENTE.substring(VIGENTE.lastIndexOf('/') + 1).replace(".txt", "");
    }

    private static String sinComentarios(String texto) {
        return texto.lines()
                .filter(linea -> !linea.startsWith("#"))
                .reduce(new StringBuilder(), (sb, l) -> sb.append(l).append('\n'), (a, b) -> a)
                .toString().trim();
    }

    private static String leer(String recurso) {
        try {
            return new String(new ClassPathResource(recurso).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el guion " + recurso, ex);
        }
    }
}
