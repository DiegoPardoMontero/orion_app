package co.orion.teaching;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La frontera del módulo del acta: nadie lo importa, salvo sus eventos. Así {@code teaching} se
 * puede apagar o reescribir sin tocar el marketplace, igual que {@code engagement}; quien quiera
 * enterarse de un acta publicada escucha el evento, no llama al servicio.
 */
class FronterasDeTeachingTest {

    private static final Pattern IMPORT = Pattern.compile("^import (co\\.orion\\.teaching\\.[\\w.]+);", Pattern.MULTILINE);

    @Test
    @DisplayName("Fuera de teaching solo se importan sus eventos")
    void soloSusEventos() throws IOException {
        Path raiz = Path.of("src/main/java/co/orion");
        List<String> ajenos;
        try (Stream<Path> archivos = Files.walk(raiz)) {
            ajenos = archivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(raiz.resolve("teaching")))
                    .flatMap(p -> {
                        try {
                            Matcher m = IMPORT.matcher(Files.readString(p));
                            return m.results().map(r -> p.getFileName() + " → " + r.group(1));
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .filter(linea -> !linea.matches(".*co\\.orion\\.teaching\\.domain\\.\\w+Event$"))
                    .toList();
        }

        assertThat(ajenos).isEmpty();
    }
}
