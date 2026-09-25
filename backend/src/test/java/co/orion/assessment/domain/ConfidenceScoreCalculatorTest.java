package co.orion.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * La fórmula del Confidence Score, probada sin base de datos y sin red: turnos sembrados, número a
 * la salida. Es lo que hace que la marca registrada signifique siempre lo mismo.
 */
class ConfidenceScoreCalculatorTest {

    private final ConfidenceScoreCalculator calculador = new ConfidenceScoreCalculator();
    private final PesosDelPuntaje pesos = PesosDelPuntaje.porDefecto();

    /* ------------------------------------------------------------------ los extremos */

    @Test
    void alguienQueArrancaRapidoYSeExtiendePuntuaAlto() {
        var turnos = repetir(6, turno(1_200, 30, 0, 0, 0, false));

        var puntaje = calculador.calcular(turnos, pesos).orElseThrow();

        assertThat(puntaje.valor()).isEqualTo(100);
        assertThat(puntaje.dimensiones()).containsEntry("arranque", 100)
                .containsEntry("extension", 100);
    }

    @Test
    void alguienQueTardaSueltaFrasesYSeDevuelveAlEspanolPuntuaBajo() {
        var turnos = repetir(6, turno(7_000, 3, 4, 2, 6, true));

        var puntaje = calculador.calcular(turnos, pesos).orElseThrow();

        assertThat(puntaje.valor()).isZero();
    }

    @Test
    void enElMedioDaElMedio() {
        // Latencia justo a mitad de camino entre las dos anclas (1,5 s y 6 s).
        var turnos = repetir(6, turno(3_750, 14, 0, 0, 0, false));

        var puntaje = calculador.calcular(turnos, pesos).orElseThrow();

        assertThat(puntaje.dimensiones().get("arranque")).isBetween(48, 52);
        assertThat(puntaje.valor()).isBetween(60, 90);
    }

    /* ------------------------------------------------ lo que el Paso 8 exige explícitamente */

    /**
     * La prueba que dice qué se está midiendo. Gramática impecable, frases largas, cero muletillas
     * — y seis segundos de silencio antes de cada respuesta. Puntúa bajo, y es correcto: esto mide
     * confianza al hablar, no dominio del idioma.
     */
    @Test
    void gramaticaBuenaConMiedoEscenicoPuntuaBajo() {
        var turnos = repetir(6, turno(6_500, 28, 0, 0, 0, false));

        var puntaje = calculador.calcular(turnos, pesos).orElseThrow();

        assertThat(puntaje.dimensiones().get("arranque")).isZero();
        assertThat(puntaje.dimensiones().get("extension")).isEqualTo(100);
        // El arranque pesa 25 de 100: quien no arranca no llega arriba por bien que hable.
        assertThat(puntaje.valor()).isLessThan(80);
        assertThat(puntaje.observadas()).contains(Observacion.SLOW_START);
    }

    /** Un turno atípico no puede definir un diagnóstico: por eso es mediana y no promedio. */
    @Test
    void unTurnoDeVeinteSegundosNoHundeElPuntaje() {
        var normales = repetir(5, turno(1_300, 26, 0, 0, 0, false));
        var conAtipico = new java.util.ArrayList<>(normales);
        conAtipico.add(turno(20_000, 26, 0, 0, 0, false));

        int sinAtipico = calculador.calcular(normales, pesos).orElseThrow().valor();
        int conElAtipico = calculador.calcular(conAtipico, pesos).orElseThrow().valor();

        assertThat(conElAtipico).isEqualTo(sinAtipico);
    }

    @Test
    void conMenosDeCuatroTurnosNoHayPuntaje() {
        var turnos = repetir(3, turno(1_200, 30, 0, 0, 0, false));

        assertThat(calculador.calcular(turnos, pesos)).isEmpty();
        assertThat(calculador.calcular(List.of(), pesos)).isEmpty();
        assertThat(calculador.calcular(null, pesos)).isEmpty();
    }

    @Test
    void elMismoConjuntoDeTurnosDaSiempreElMismoNumero() {
        var turnos = List.of(
                turno(1_100, 22, 1, 0, 2, false),
                turno(4_800, 7, 0, 1, 3, false),
                turno(2_200, 31, 2, 0, 1, false),
                turno(9_000, 4, 0, 2, 5, true),
                turno(1_900, 18, 1, 0, 0, false));

        var primero = calculador.calcular(turnos, pesos).orElseThrow();
        for (int i = 0; i < 20; i++) {
            var otro = calculador.calcular(turnos, pesos).orElseThrow();
            assertThat(otro.valor()).isEqualTo(primero.valor());
            assertThat(otro.observadas()).isEqualTo(primero.observadas());
            assertThat(otro.version()).isEqualTo(primero.version());
        }
    }

    /* ----------------------------------------------------------- cada dimensión por separado */

    @Test
    void laContinuidadMideLaProporcionDeTurnosConFrasesSueltas() {
        var ninguna = repetir(6, turno(1_200, 20, 0, 0, 0, false));
        var todas = repetir(6, turno(1_200, 20, 0, 1, 0, false));

        assertThat(dimension(ninguna, "continuidad")).isEqualTo(100);
        assertThat(dimension(todas, "continuidad")).isZero();
    }

    @Test
    void laAutonomiaMideLaRetiradaAlIdiomaPropio() {
        var nunca = repetir(6, turno(1_200, 20, 0, 0, 0, false));
        var mitad = new java.util.ArrayList<TurnoDelUsuario>();
        mitad.addAll(repetir(3, turno(1_200, 20, 0, 0, 0, false)));
        mitad.addAll(repetir(3, turno(1_200, 20, 0, 0, 0, true)));

        assertThat(dimension(nunca, "autonomia")).isEqualTo(100);
        assertThat(dimension(mitad, "autonomia")).isZero();   // 50 % es el ancla del cero
    }

    @Test
    void laSolturaCuentaTropiezosPorCadaCienPalabras() {
        var limpio = repetir(5, turno(1_200, 20, 0, 0, 0, false));
        // 20 tropiezos por cada 100 palabras: el ancla del cero.
        var trabado = repetir(5, turno(1_200, 20, 2, 0, 2, false));

        assertThat(dimension(limpio, "soltura")).isEqualTo(100);
        assertThat(dimension(trabado, "soltura")).isZero();
    }

    /* --------------------------------------------------------------------- observaciones */

    @Test
    void reconoceAntesDeSenalar() {
        // Arranca rápido y sostiene: la primera observación tiene que ser de las que reconocen.
        var turnos = repetir(6, turno(1_100, 24, 0, 0, 0, false));

        var observadas = calculador.calcular(turnos, pesos).orElseThrow().observadas();

        assertThat(observadas).isNotEmpty();
        assertThat(observadas.getFirst()).isEqualTo(Observacion.STEADY_START);
    }

    @Test
    void notaCuandoLasFrasesSeAlarganEnTerrenoComodo() {
        // El guion pone el terreno cómodo al principio y el empuje al final.
        var turnos = List.of(
                turno(1_500, 30, 0, 0, 0, false),
                turno(1_500, 28, 0, 0, 0, false),
                turno(1_500, 26, 0, 0, 0, false),
                turno(1_500, 8, 0, 0, 0, false),
                turno(1_500, 6, 0, 0, 0, false),
                turno(1_500, 7, 0, 0, 0, false));

        assertThat(calculador.calcular(turnos, pesos).orElseThrow().observadas())
                .contains(Observacion.LONG_ANSWERS_WHEN_COMFORTABLE);
    }

    /* ------------------------------------------------------------ la rama en español (25/09) */

    /**
     * Quien habla español con soltura no puede salir con un número alto de confianza en inglés: sus
     * turnos en español cuentan como turnos sin inglés, y el número queda en el tramo de quien empieza.
     */
    @Test
    void enLaRamaEnEspanolSoloCuentaLoQueSeDijoEnIngles() {
        var espanolFluido = repetir(6, turno(1_000, 30, 0, 0, 0, true));

        var comoSiFueraIngles = calculador.calcular(
                repetir(6, turno(1_000, 30, 0, 0, 0, false)), pesos).orElseThrow();
        var soloElIngles = calculador.calcularSoloElIngles(espanolFluido, pesos).orElseThrow();

        assertThat(comoSiFueraIngles.valor()).isEqualTo(100);
        assertThat(soloElIngles.valor()).isZero();
        assertThat(soloElIngles.dimensiones()).containsEntry("extension", 0).containsEntry("autonomia", 0);
    }

    @Test
    void losTurnosEnInglesDeLaRamaEnEspanolCuentanComoSiempre() {
        var mezcla = List.of(
                turno(1_200, 30, 0, 0, 0, false), turno(1_200, 30, 0, 0, 0, false),
                turno(1_200, 30, 0, 0, 0, false), turno(1_000, 40, 0, 0, 0, true),
                turno(1_000, 40, 0, 0, 0, true));

        var puntaje = calculador.calcularSoloElIngles(mezcla, pesos).orElseThrow();

        // Tres de cinco en buen inglés sostienen la mediana; los dos en español pesan en lo suyo.
        assertThat(puntaje.dimensiones().get("extension")).isEqualTo(100);
        assertThat(puntaje.valor()).isBetween(1, 99);
    }

    @Test
    void laRamaEnEspanolTampocoInventaNumeroConPocosTurnos() {
        assertThat(calculador.calcularSoloElIngles(repetir(3, turno(1_000, 30, 0, 0, 0, true)), pesos))
                .isEmpty();
    }

    /* ------------------------------------------------------------------------- la versión */

    @Test
    void dosJuegosDePesosNoPuedenCompartirVersion() {
        String conLosDelDiseno = ConfidenceScoreCalculator.versionDe(PesosDelPuntaje.porDefecto());
        String conOtros = ConfidenceScoreCalculator.versionDe(
                new PesosDelPuntaje(40, 20, 20, 15, 5));

        assertThat(conLosDelDiseno).startsWith("v1.").hasSizeLessThanOrEqualTo(10);
        assertThat(conOtros).isNotEqualTo(conLosDelDiseno);
        // Y la misma fórmula da siempre la misma versión.
        assertThat(ConfidenceScoreCalculator.versionDe(PesosDelPuntaje.porDefecto()))
                .isEqualTo(conLosDelDiseno);
    }

    /* --------------------------------------------------------------------------- apoyo */

    private int dimension(List<TurnoDelUsuario> turnos, String cual) {
        return calculador.calcular(turnos, pesos).orElseThrow().dimensiones().get(cual);
    }

    private static TurnoDelUsuario turno(int latencia, int palabras, int autocorrecciones,
                                         int abandonadas, int muletillas, boolean seDevolvio) {
        return new TurnoDelUsuario(latencia, palabras, autocorrecciones, abandonadas,
                muletillas, seDevolvio);
    }

    private static List<TurnoDelUsuario> repetir(int veces, TurnoDelUsuario turno) {
        return IntStream.range(0, veces).mapToObj(i -> turno).toList();
    }
}
