package co.orion.legal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Ningún {@code {{marcador}}} puede llegar a la pantalla sin rellenar.
 *
 * <p>El fallo que previene es silencioso y feo: alguien escribe {@code {{comision_profesor}}} en una
 * cláusula, nadie lo rellena porque ese nombre no existe, y el usuario lee las llaves en un
 * documento legal. No hay error, no hay log — solo un contrato que parece roto.
 *
 * <p>La lista de nombres válidos se declara aquí y no se lee de {@code PublicFiguresService} a
 * propósito: si se leyera de la misma fuente, renombrar un marcador pasaría el test y rompería los
 * documentos igual. Dos listas que deben coincidir es justo lo que se quiere comprobar.
 */
class MarcadoresDeLosDocumentosTest {

    private static final Pattern MARCADOR = Pattern.compile("\\{\\{([a-z_]+)\\}\\}");

    /** Los que rellena {@code LegalDocumentService.render}: identidad, vigencia y cifras. */
    private static final Set<String> CONOCIDOS = Set.of(
            "responsable", "documento", "domicilio", "ciudad", "correo", "whatsapp", "horario",
            "vigencia",
            "comision", "duracion_clase", "retencion_pago", "cancelacion_estudiante",
            "cancelacion_profesor", "antelacion_minima", "reporte_ausencia", "ventana_reclamo",
            "cierre_automatico", "revision_postulacion");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"legal/terms-1.0.md", "legal/privacy-1.0.md", "legal/terms-1.1.md", "legal/privacy-1.1.md"})
    @DisplayName("Todo marcador del documento tiene quien lo rellene")
    void todoMarcadorTieneQuienLoRellene(String recurso) throws IOException {
        Set<String> usados = new LinkedHashSet<>();
        Matcher m = MARCADOR.matcher(leer(recurso));
        while (m.find()) {
            usados.add(m.group(1));
        }

        assertThat(usados)
                .describedAs("marcadores de %s que nadie rellena", recurso)
                .isSubsetOf(CONOCIDOS);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"legal/terms-1.0.md", "legal/privacy-1.0.md", "legal/terms-1.1.md", "legal/privacy-1.1.md"})
    @DisplayName("Las cifras de negocio no se escriben a mano en las cláusulas")
    void lasCifrasNoSeEscribenAMano(String recurso) throws IOException {
        // Regla de Pardo: lo que se cambia desde Ajustes cambia en todas partes. Una cláusula que
        // dice «12 horas» en vez de «{{cancelacion_estudiante}}» miente el día que el ajuste cambie.
        // Se miran solo los números en negrita, que es como se citan las cifras en estos textos:
        // así «cinco (5) días hábiles» del retracto —que fija la ley y NO es ajuste— no salta.
        Stream<String> prohibidos = Stream.of(
                "\\*\\*\\d+ ?%\\*\\*",
                "\\*\\*\\d+ ?(horas?|minutos?)\\*\\*",
                "comisión del \\d+");

        String cuerpo = leer(recurso);
        prohibidos.forEach(regex -> assertThat(Pattern.compile(regex).matcher(cuerpo).find())
                .describedAs("%s cita a mano una cifra que vive en Ajustes (patrón %s)",
                        recurso, regex)
                .isFalse());
    }

    private static String leer(String recurso) throws IOException {
        return new String(new ClassPathResource(recurso).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
