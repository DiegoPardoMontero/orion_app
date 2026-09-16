package co.orion.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las señales que el servidor deduce del texto.
 *
 * <p>Los ejemplos no son inventados: salen de la conversación real grabada el 12/09/2026, que es la
 * única que tenemos. Por eso este test importa más de lo que parece — es el registro de qué casos
 * sabemos leer hoy, y el sitio donde caerá el primero que no.
 */
class SignalExtractorTest {

    // ── Autocorrecciones ──

    @Test
    void elMarcadorExplicito() {
            assertThat(SignalExtractor.selfCorrections("I go there, I mean I went there"))
                    .isEqualTo(1);
    }

    @Test
    void laPalabraRepetida() {
            // «I think because I think that» — de la transcripción real.
            assertThat(SignalExtractor.selfCorrections("the the meeting was long")).isEqualTo(1);
    }

    @Test
    void unaFraseLimpiaNoTieneNinguna() {
            assertThat(SignalExtractor.selfCorrections("I work in logistics and I like it"))
                    .isZero();
    }

    @Test
    void sinTextoNoInventaNada() {
            assertThat(SignalExtractor.selfCorrections(null)).isZero();
            assertThat(SignalExtractor.selfCorrections("  ")).isZero();
    }

    // ── Frases abandonadas ──

    @Test
    void laQueSeCortaConPuntosSuspensivos() {
            assertThat(SignalExtractor.abandonedClauses("I wanted to say that... never mind"))
                    .isEqualTo(1);
    }

    @Test
    void laQueTerminaColgandoDeUnaPreposicion() {
            // Lo que de verdad pasa al hablar un idioma que no dominas: se llega a la preposición
            // y no aparece lo que iba después.
            assertThat(SignalExtractor.abandonedClauses("I need it for my work and")).isEqualTo(1);
    }

    @Test
    void unaPreguntaNoEstaAbandonada() {
            // Termina en «it», que es palabra de las que cuelgan, pero es una pregunta completa.
            assertThat(SignalExtractor.abandonedClauses("can you repeat it?")).isZero();
    }

    @Test
    void unaFraseTerminadaNoCuenta() {
            assertThat(SignalExtractor.abandonedClauses("I work in logistics.")).isZero();
    }

    // ── Muletillas ──

    @Test
    void lasCuentaTodas() {
            assertThat(SignalExtractor.fillerCount("um, so, like, I work here")).isEqualTo(3);
    }

    @Test
    void noConfundeUnaPalabraDeVerdadConRelleno() {
            assertThat(SignalExtractor.fillerCount("I like my job")).isEqualTo(1); // «like» sí cuenta
            assertThat(SignalExtractor.fillerCount("I work every day")).isZero();
    }

    // ── Cambio al idioma propio ──

    @Test
    void loDetectaPorLosCaracteresQueElInglesNoTiene() {
            assertThat(SignalExtractor.nativeSwitch("¿Te respondo en español?", "EN")).isTrue();
    }

    @Test
    void loDetectaPorPalabrasFuncionales() {
            assertThat(SignalExtractor.nativeSwitch("pues como que no entiendo", "EN")).isTrue();
    }

    @Test
    void unaPalabraSueltaNoEsRendirse() {
            // «no» es española y también inglesa; el nombre de una ciudad no es un cambio de idioma.
            assertThat(SignalExtractor.nativeSwitch("no, I live in Medellin", "EN")).isFalse();
    }

    @Test
    void enUnaClaseDeEspanolHablarEspanolNoEsRendirse() {
            assertThat(SignalExtractor.nativeSwitch("pues como que no entiendo", "ES")).isFalse();
    }

    @Test
    @DisplayName("Las palabras se cuentan como las cuenta cualquiera")
    void contarPalabras() {
    assertThat(SignalExtractor.wordCount("Okay, hi, so my name is Eduardo")).isEqualTo(7);
    assertThat(SignalExtractor.wordCount("")).isZero();
    assertThat(SignalExtractor.wordCount(null)).isZero();
    }
}
