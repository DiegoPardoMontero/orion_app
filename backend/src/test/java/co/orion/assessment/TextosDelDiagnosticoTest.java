package co.orion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.assessment.domain.Observacion;

/**
 * Lo que el diagnóstico NUNCA puede decirle a una persona.
 *
 * <p>Es el peor fallo posible de este bloque y el único que ningún otro test detecta. El
 * diagnóstico es el único momento del producto en que Orión le dice a alguien algo sobre sí mismo,
 * y si ese texto se siente como un juicio la función hace más daño que bien — da igual que el
 * número esté bien calculado.
 *
 * <p>Tres familias de frase prohibida, y cada una por su motivo:
 *
 * <ul>
 *   <li><strong>El elogio y el reproche.</strong> «Muy bien», «excelente», «buen intento». Evaluar
 *       en voz alta cierra a la persona, y una persona cerrada deja de mostrar cómo habla.</li>
 *   <li><strong>La palabra examen.</strong> Es una conversación. Llamarla prueba o test cambia
 *       cómo se comporta quien la está teniendo, que es justo lo que se está midiendo.</li>
 *   <li><strong>El nivel.</strong> Esto no es el MCER y no equivale a A2 ni a B1. Decirlo sería
 *       una afirmación falsa sobre la persona con nuestro membrete.</li>
 * </ul>
 *
 * <p>La lista vive aquí y no en un archivo de configuración a propósito: es una regla de marca, no
 * un ajuste, y cambiarla tiene que costar una revisión de código.
 */
class TextosDelDiagnosticoTest {

    private static final List<String> ELOGIO_O_REPROCHE = List.of(
            "muy bien", "excelente", "perfecto", "buen intento", "casi lo logras",
            "qué bien hablas", "lo hiciste mal", "necesitas mejorar", "deberías practicar más",
            "tu nivel es bajo", "no está mal");

    private static final List<String> PALABRA_EXAMEN = List.of(
            "examen", "test de nivel", "evaluación de nivel", "calificación", "nota final",
            "aprobaste", "reprobaste");

    private static final List<String> NIVEL_MCER = List.of(
            "nivel a1", "nivel a2", "nivel b1", "nivel b2", "nivel c1", "nivel c2", "mcer");

    /** Los textos que la persona llega a leer o a oír. */
    private static Stream<String> textosDeCaraAlUsuario() throws IOException {
        Stream<String> observaciones = Stream.of(Observacion.values()).map(Observacion::descripcion);
        Stream<String> pantallas = Stream.of(
                "frontend/src/app/diagnostico/empezar/Puerta.tsx",
                "frontend/src/app/diagnostico/empezar/Resultado.tsx",
                "frontend/src/app/diagnostico/empezar/Conversacion.tsx",
                "frontend/src/app/diagnostico/page.tsx",
                "frontend/src/components/gamificacion/TarjetaDiagnostico.tsx")
                .map(TextosDelDiagnosticoTest::leerDelRepo)
                .map(TextosDelDiagnosticoTest::soloLoQueSeVe);
        return Stream.concat(observaciones, pantallas);
    }

    @Test
    @DisplayName("Ningún texto del diagnóstico elogia ni reprocha")
    void nadaDeElogioNiReproche() throws IOException {
        prohibir(ELOGIO_O_REPROCHE);
    }

    @Test
    @DisplayName("Ningún texto lo llama examen, prueba de nivel ni calificación")
    void nadaDeExamen() throws IOException {
        prohibir(PALABRA_EXAMEN);
    }

    @Test
    @DisplayName("Ningún texto lo equipara a un nivel del MCER")
    void nadaDeNiveles() throws IOException {
        prohibir(NIVEL_MCER);
    }

    @Test
    @DisplayName("El guion vigente tampoco: es lo que la IA dice en voz alta")
    void elGuionTampoco() throws IOException {
        String guion = leerDelRepo("backend/src/main/resources/prompts/assessment-scenario-v5.txt");
        // Solo las líneas que la IA puede decir: las prohibiciones del propio guion NOMBRAN estas
        // frases para prohibirlas, y contarlas ahí sería castigar al archivo por hacer su trabajo.
        String hablado = guion.lines()
                .filter(l -> !l.startsWith("#") && !l.trim().startsWith("- **Nunca")
                        && !l.contains("Prohibido") && !l.contains("Nunca «"))
                .reduce("", (a, b) -> a + "\n" + b)
                .toLowerCase(Locale.ROOT);

        for (String frase : NIVEL_MCER) {
            assertThat(quitarNegaciones(hablado)).doesNotContain(frase);
        }
    }

    /**
     * Las notas que la pantalla le manda a Meissa —el tiempo y el momento de nombrar a Orión— son
     * cadenas exactas que el guion reconoce. Si una cambia de un lado y no del otro, el modelo
     * recibe una nota que no entiende y la ignora en silencio: se pierde el aviso de los veinte
     * segundos, o la despedida.
     */
    @Test
    @DisplayName("Las notas que manda la pantalla son exactamente las que conoce el guion")
    void lasNotasCoinciden() {
        String guion = leerDelRepo("backend/src/main/resources/prompts/assessment-scenario-v5.txt");
        String pantalla = leerDelRepo("frontend/src/app/diagnostico/empezar/Conversacion.tsx");
        for (String nota : List.of("[20 seconds left]", "[Time is up]", "[Orión now]")) {
            assertThat(guion).as("el guion conoce " + nota).contains("«" + nota + "»");
            assertThat(pantalla).as("la pantalla manda " + nota).contains("\"" + nota + "\"");
        }
    }

    /**
     * Quita del archivo lo que la persona no llega a leer: comentarios de código y de JSX. Un
     * comentario que dice «esto no es un examen» es exactamente lo que queremos que exista, y
     * castigarlo enseñaría a no escribirlo.
     */
    private static String soloLoQueSeVe(String fuente) {
        return fuente
                .replaceAll("(?s)\\{/\\*.*?\\*/\\}", " ")
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)^\\s*//.*$", " ");
    }

    /**
     * Las formas negadas están permitidas, y de hecho son el copy correcto: «sin examen» y «no hay
     * respuestas correctas» dicen justo lo que el diagnóstico quiere decir. Lo que se persigue es
     * la afirmación, no la palabra.
     */
    private static final List<String> NEGACIONES = List.of(
            "sin preguntas de examen", "no hay preguntas de examen",
            "sin examen", "no es un examen", "no hay examen", "sin nota", "no es una prueba",
            "no es el mcer", "no es un nivel del mcer", "no equivale");

    private static String quitarNegaciones(String texto) {
        String limpio = texto;
        for (String permitida : NEGACIONES) {
            limpio = limpio.replace(permitida, " ");
        }
        return limpio;
    }

    private void prohibir(List<String> frases) throws IOException {
        List<String> textos = textosDeCaraAlUsuario()
                .map(t -> quitarNegaciones(t.toLowerCase(Locale.ROOT)))
                .toList();
        for (String frase : frases) {
            for (String texto : textos) {
                assertThat(texto)
                        .describedAs("frase prohibida en un texto del diagnóstico: «%s»", frase)
                        .doesNotContain(frase);
            }
        }
    }

    /** Los .tsx viven fuera del módulo; se leen desde la raíz del repositorio. */
    private static String leerDelRepo(String ruta) {
        try {
            Path desdeBackend = Path.of("..").resolve(ruta).normalize();
            return Files.exists(desdeBackend)
                    ? Files.readString(desdeBackend, StandardCharsets.UTF_8)
                    : "";
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer " + ruta, ex);
        }
    }
}
