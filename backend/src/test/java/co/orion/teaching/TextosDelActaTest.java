package co.orion.teaching;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ningún texto del Bloque 10 —el acta y la práctica— usa las frases prohibidas (brief, pasos A2,
 * A5.4 y C2: «un test que recorra todos los textos del bloque»). Es barato y cubre el peor fallo
 * posible: un resumen de clase o un ejercicio que regaña al estudiante con nuestro membrete, o una
 * pantalla vacía que lamenta en vez de invitar.
 *
 * <p>Se miran las pantallas, las notificaciones, los mensajes del servidor, el generador sin IA y los
 * prompts. Lo que va entre comillas angulares no cuenta: ahí un archivo NOMBRA las frases para
 * prohibirlas, y castigarlo sería castigar al archivo por hacer su trabajo.
 */
class TextosDelActaTest {

    /** Las literales del brief (A2) y las que regañan o lamentan (A5.4). */
    private static final List<String> PROHIBIDAS = List.of(
            "no sabes", "tu nivel es muy malo", "eso está completamente mal", "es muy fácil",
            "aprenderás inglés perfecto", "sigues equivocándote", "lo hiciste mal", "lamentablemente",
            "lo sentimos", "no tienes práctica");

    private static final List<String> TEXTOS = List.of(
            "frontend/src/app/(app)/mis-clases/[id]/acta/page.tsx",
            "frontend/src/lib/actas.ts",
            "backend/src/main/java/co/orion/messaging/application/LessonNoteNotificationListener.java",
            // La práctica (Parte B).
            "frontend/src/app/(app)/practica/[id]/page.tsx",
            "frontend/src/components/InvitacionAPracticar.tsx",
            "frontend/src/lib/practica.ts",
            "frontend/src/app/(app)/estudiantes/[id]/page.tsx",
            "backend/src/main/java/co/orion/practice/application/PracticeService.java",
            "backend/src/main/java/co/orion/practice/application/GeneradorSinIa.java",
            "backend/src/main/java/co/orion/practice/domain/PracticeSet.java",
            "backend/src/main/java/co/orion/practice/domain/PracticeItem.java");

    private static final List<String> PROMPTS = List.of(
            "backend/src/main/resources/prompts/lesson-note-v3.txt",
            "backend/src/main/resources/prompts/practice-v4.txt",
            "backend/src/main/resources/prompts/practice-check-v1.txt");

    @Test
    @DisplayName("Las pantallas y los avisos del acta y de la práctica no regañan ni lamentan")
    void pantallasYAvisos() throws IOException {
        for (String ruta : TEXTOS) {
            String texto = sinLoNombrado(leer(ruta)).toLowerCase(Locale.ROOT);
            for (String frase : PROHIBIDAS) {
                assertThat(texto).as(ruta + " dice «" + frase + "»").doesNotContain(frase);
            }
        }
    }

    @Test
    @DisplayName("Los prompts tampoco, fuera de las comillas donde las nombran para prohibirlas")
    void losPrompts() throws IOException {
        for (String ruta : PROMPTS) {
            String hablado = sinLoNombrado(leer(ruta).lines()
                    .filter(l -> !l.startsWith("#"))
                    .reduce("", (a, b) -> a + "\n" + b))
                    .toLowerCase(Locale.ROOT);
            for (String frase : PROHIBIDAS) {
                assertThat(hablado).as(ruta + " dice «" + frase + "»").doesNotContain(frase);
            }
        }
    }

    private static String sinLoNombrado(String texto) {
        return texto.replaceAll("(?s)«[^»]*»", " ");
    }

    private static String leer(String ruta) throws IOException {
        Path desdeBackend = Path.of("..").resolve(ruta).normalize();
        assertThat(desdeBackend).as("existe " + ruta).exists();
        return Files.readString(desdeBackend, StandardCharsets.UTF_8);
    }
}
