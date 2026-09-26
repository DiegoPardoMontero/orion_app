package co.orion.legal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * El contenido legal, comprobado como contenido y no como formato.
 *
 * <p>El art. 13 del Decreto 1377 de 2013 enumera lo que una Política de Tratamiento <em>debe</em>
 * contener. No es una recomendación de redacción: una política a la que le falte una de esas
 * secciones está incompleta y la SIC la trata como tal. Este test es lo único que impide que una
 * reescritura futura se lleve por delante un requisito sin que nadie lo note.
 *
 * <p>Es deliberadamente tonto: busca cadenas. Un test listo aquí sería un test que se puede
 * engañar; este falla si alguien borra la sección, que es exactamente lo que queremos.
 */
class PoliticaDeTratamientoTest {

    private static String leer(String recurso) {
        try {
            return new String(new ClassPathResource(recurso).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("No se pudo leer " + recurso, ex);
        }
    }

    @Test
    @DisplayName("La Política lleva las seis secciones del art. 13 del Decreto 1377 de 2013")
    void laPoliticaLlevaLasSeisSecciones() {
        String politica = leer("legal/privacy-1.1.md");

        // 1. Identidad y datos de contacto del responsable.
        assertThat(politica).contains("Responsable del tratamiento", "{{domicilio}}", "{{correo}}");
        // 2. Tratamiento al que se someten los datos y su finalidad.
        assertThat(politica).contains("Qué datos recogemos", "Para qué los usamos");
        // 3. Derechos del titular.
        assertThat(politica).contains("Tus derechos como titular", "Ley 1581");
        // 4. Área responsable de atender peticiones, consultas y reclamos.
        assertThat(politica).contains("El área responsable de atender estas solicitudes");
        // 5. Procedimiento para ejercer los derechos, con sus plazos legales.
        assertThat(politica).contains("Cómo ejercerlos", "10 días hábiles", "15 días hábiles");
        // 6. Fecha de entrada en vigencia y periodo de vigencia de la base de datos.
        assertThat(politica).contains("Vigencia", "{{vigencia}}");
    }

    @Test
    @DisplayName("La Política dice que Orión no acepta menores y por qué")
    void laPoliticaCierraLaPuertaALosMenores() {
        String politica = leer("legal/privacy-1.1.md");

        assertThat(politica).contains("solo para mayores de 18 años");
        assertThat(politica).contains("artículo 7 de la Ley 1581");
    }

    @Test
    @DisplayName("Los Términos publican la identidad que exige el art. 50 de la Ley 1480")
    void losTerminosPublicanLaIdentidadDelProveedor() {
        String terminos = leer("legal/terms-1.1.md");

        assertThat(terminos).contains("{{responsable}}", "{{documento}}", "{{domicilio}}",
                "{{correo}}");
        assertThat(terminos).contains("notificaciones judiciales");
        assertThat(terminos).contains("artículo 50 de la Ley 1480 de 2011");
    }

    /**
     * El retracto es el derecho que más fácil se pierde en la redacción, porque choca con la
     * política comercial de cancelación. Tiene que estar, con su plazo, su excepción legal y el
     * medio de devolución.
     */
    @Test
    @DisplayName("Los Términos explican el retracto con su plazo, su excepción y su devolución")
    void losTerminosExplicanElRetracto() {
        String terminos = leer("legal/terms-1.1.md");

        assertThat(terminos).contains("artículo 47 de la Ley 1480 de 2011");
        assertThat(terminos).contains("cinco (5) días hábiles");
        assertThat(terminos).contains("la prestación del servicio ya comenzó");
        assertThat(terminos).contains("quince (15) días calendario");
        assertThat(terminos).contains("al mismo medio de pago");
        // Y que la tabla de cancelaciones no se lo coma.
        assertThat(terminos).contains("sin perjuicio de tu derecho de retracto");
    }

    /**
     * La política de cancelación es de Pardo, no de la ley — pero una vez escrita en el contrato
     * obliga, y el código tiene que hacer lo que dice. Este test fija el texto; `LessonLifecycleIT`
     * fija el comportamiento. Si alguien cambia uno sin el otro, algo falla.
     */
    @Test
    @DisplayName("Los Términos describen la política de cancelación tal como la ejecuta el código")
    void losTerminosDescribenLaPoliticaDeCancelacion() {
        String terminos = leer("legal/terms-1.1.md");

        assertThat(terminos).contains("**Cancelar se puede siempre**");
        // La cifra no se escribe en la cláusula: se cita el ajuste, y `LegalDocumentService` la
        // rellena en cada lectura. Lo que este test fija es que la cláusula siga hablando de las
        // dos caras de la frontera, no cuál es la frontera — eso lo decide Ajustes.
        assertThat(terminos).contains("Cancelas con más de {{cancelacion_estudiante}}");
        assertThat(terminos).contains("Cancelas con menos de {{cancelacion_estudiante}}");
        assertThat(terminos).contains("el profesor recibe su pago y no hay devolución");
        assertThat(terminos).contains("**El profesor cancela**, con el tiempo que sea");
    }

    @Test
    @DisplayName("Los Términos dicen que Orión es un portal de contacto y qué implica")
    void losTerminosDicenQueEsUnPortalDeContacto() {
        String terminos = leer("legal/terms-1.1.md");

        assertThat(terminos).contains("portal de contacto");
        assertThat(terminos).contains("artículo 53 de la Ley 1480 de 2011");
        assertThat(terminos).contains("contratista independiente");
    }

    /**
     * Un contrato de consumo no puede renunciar a los derechos del consumidor. Si algún día alguien
     * añade una cláusula que lo intente, esta frase seguirá ahí contradiciéndola — y este test
     * seguirá exigiendo que esté.
     */
    @Test
    @DisplayName("Los Términos no renuncian a los derechos del consumidor")
    void losTerminosNoRenuncianADerechos() {
        String terminos = leer("legal/terms-1.1.md");

        assertThat(terminos).contains(
                "Nada en estos Términos limita, renuncia ni condiciona los derechos que la ley te "
                        + "reconoce como consumidor");
        assertThat(terminos).contains("Superintendencia de Industria y Comercio");
    }

    /**
     * Pardo (26/09/2026): los textos dicen «Orión», no su nombre. El nombre solo sale en la tabla de
     * identificación, porque el art. 50 de la Ley 1480 y el art. 13 del Decreto 1377 exigen decir
     * quién responde; lo rellena {@code ORION_LEGAL_NOMBRE}.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"legal/terms-1.1.md", "legal/privacy-1.1.md"})
    @DisplayName("El nombre del responsable solo sale en la tabla de identificación")
    void elNombreSoloSaleEnLaTabla(String recurso) {
        String texto = leer(recurso);

        assertThat(texto.lines().filter(linea -> linea.contains("{{responsable}}")))
                .singleElement().asString().startsWith("| **");
    }
}
