package co.orion.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La frontera de la práctica (brief, C2): nadie la importa salvo sus eventos. Así se puede apagar
 * o reescribir sin tocar el marketplace; {@code engagement} solo escucha que se completó.
 */
class FronterasDePracticaTest {

    private static final Pattern IMPORT = Pattern.compile("^import (co\\.orion\\.practice\\.[\\w.]+);", Pattern.MULTILINE);

    @Test
    @DisplayName("Fuera de practice solo se importan sus eventos")
    void soloSusEventos() throws IOException {
        Path raiz = Path.of("src/main/java/co/orion");
        List<String> ajenos;
        try (Stream<Path> archivos = Files.walk(raiz)) {
            ajenos = archivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(raiz.resolve("practice")))
                    .flatMap(p -> {
                        try {
                            return IMPORT.matcher(Files.readString(p)).results()
                                    .map(r -> p.getFileName() + " → " + r.group(1));
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .filter(linea -> !linea.matches(".*co\\.orion\\.practice\\.domain\\.\\w+Event$"))
                    .toList();
        }

        assertThat(ajenos).isEmpty();
    }
}
