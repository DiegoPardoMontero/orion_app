package co.orion.assessment.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El Confidence Score, calculado.
 *
 * <p>Clase pura: sin Spring, sin repositorios y sin reloj del sistema, igual que
 * {@code SlotCalculator} y {@code RankingCalculator}. Recibe turnos ya medidos y devuelve un
 * número. Es lo que permite probar la fórmula con turnos sembrados y lo que garantiza que el mismo
 * conjunto de turnos dé siempre el mismo puntaje — sin eso, una marca registrada sobre un número
 * que cambia solo no vale nada.
 *
 * <p><strong>No es un nivel.</strong> No equivale a A2, B1 ni a nada del MCER. Alguien con gramática
 * impecable y pánico escénico puntúa bajo, y eso es correcto: es exactamente lo que se quiere
 * medir. Tampoco mide comprensión, vocabulario ni pronunciación, y nunca se le muestra a otro
 * estudiante.
 *
 * <p><strong>Se usa la mediana, no el promedio.</strong> Un solo turno en el que la persona se quedó
 * pensando veinte segundos no puede definir su diagnóstico.
 */
public final class ConfidenceScoreCalculator {

    /** Por debajo de esto no hay puntaje. Un número sacado de dos turnos es peor que ningún número. */
    public static final int TURNOS_MINIMOS = 4;

    private static final String FAMILIA = "v1";

    // Anclas de cada dimensión: el valor que vale 100 y el que vale 0. Entre ellos, línea recta.
    private static final double ARRANQUE_100_MS = 1_500;
    private static final double ARRANQUE_0_MS = 6_000;
    private static final double CONTINUIDAD_100 = 0.00;
    private static final double CONTINUIDAD_0 = 0.40;
    private static final double EXTENSION_100_PALABRAS = 25;
    private static final double EXTENSION_0_PALABRAS = 4;
    private static final double AUTONOMIA_100 = 0.00;
    private static final double AUTONOMIA_0 = 0.50;
    private static final double SOLTURA_100_POR_CIEN = 3;
    private static final double SOLTURA_0_POR_CIEN = 20;

    // Umbrales para que una observación se encienda. Miran la dimensión ya normalizada.
    private static final int NOTABLE_BAJO = 45;
    private static final int NOTABLE_ALTO = 75;

    /**
     * @return el puntaje, o vacío si no hay turnos suficientes para sostener uno. Quien llame debe
     *         marcar la evaluación como ABANDONED en ese caso, nunca inventar un número.
     */
    public Optional<Puntaje> calcular(List<TurnoDelUsuario> turnos, PesosDelPuntaje pesos) {
        if (turnos == null || turnos.size() < TURNOS_MINIMOS || pesos.total() <= 0) {
            return Optional.empty();
        }

        int arranque = normalizar(medianaDeLatencia(turnos), ARRANQUE_100_MS, ARRANQUE_0_MS);
        int continuidad = normalizar(
                proporcionDeTurnosCon(turnos, t -> t.abandonedClauses() > 0),
                CONTINUIDAD_100, CONTINUIDAD_0);
        int extension = normalizar(medianaDePalabras(turnos),
                EXTENSION_100_PALABRAS, EXTENSION_0_PALABRAS);
        int autonomia = normalizar(proporcionDeTurnosCon(turnos, TurnoDelUsuario::nativeSwitch),
                AUTONOMIA_100, AUTONOMIA_0);
        int soltura = normalizar(tropiezosPorCienPalabras(turnos),
                SOLTURA_100_POR_CIEN, SOLTURA_0_POR_CIEN);

        Map<String, Integer> dimensiones = new LinkedHashMap<>();
        dimensiones.put("arranque", arranque);
        dimensiones.put("continuidad", continuidad);
        dimensiones.put("extension", extension);
        dimensiones.put("autonomia", autonomia);
        dimensiones.put("soltura", soltura);

        long ponderado = (long) arranque * pesos.arranque()
                + (long) continuidad * pesos.continuidad()
                + (long) extension * pesos.extension()
                + (long) autonomia * pesos.autonomia()
                + (long) soltura * pesos.soltura();
        int valor = (int) Math.round((double) ponderado / pesos.total());

        return Optional.of(new Puntaje(valor, versionDe(pesos), dimensiones,
                observar(turnos, dimensiones)));
    }

    /**
     * El puntaje de la rama en español, que también lleva número (Pardo, 25/09/2026: «no pasa nada
     * que le muestre el Confidence Score»). Mide lo mismo —confianza al hablar inglés—, así que
     * cuenta solo lo que se dijo en inglés.
     *
     * <p>Un turno en español no es un turno en inglés más corto: es uno en el que no se arrancó ni
     * se terminó una frase en inglés y se volvió al idioma propio. Contarlo con sus palabras en
     * español premiaría la soltura en el idioma que ya se sabe —una conversación fluida en español
     * daría un número alto— justo cuando lo que se ve es que la persona está empezando. Los turnos en
     * inglés, si los hubo, cuentan igual que en la conversación normal.
     */
    public Optional<Puntaje> calcularSoloElIngles(List<TurnoDelUsuario> turnos, PesosDelPuntaje pesos) {
        if (turnos == null) {
            return Optional.empty();
        }
        return calcular(turnos.stream()
                .map(t -> t.nativeSwitch() ? new TurnoDelUsuario((int) ARRANQUE_0_MS, 0, 0, 1, 0, true) : t)
                .toList(), pesos);
    }

    /**
     * La versión de la fórmula, con la huella de los pesos.
     *
     * <p>Los pesos se editan desde la pantalla de Ajustes, así que la constante «v1» sola sería una
     * mentira: dos juegos de pesos distintos producirían puntajes distintos etiquetados igual, y la
     * curva histórica que este campo existe para proteger dejaría de significar algo sin que nadie
     * se enterara. Con la huella, dos fórmulas no pueden compartir versión.
     */
    public static String versionDe(PesosDelPuntaje pesos) {
        int huella = 17;
        for (int peso : new int[] {pesos.arranque(), pesos.continuidad(), pesos.extension(),
                pesos.autonomia(), pesos.soltura()}) {
            huella = huella * 31 + peso;
        }
        return FAMILIA + "." + String.format("%04x", huella & 0xFFFF);
    }

    /** Lo que se notó. Orden fijo: primero lo que reconoce algo, después lo que hay que trabajar. */
    private List<Observacion> observar(List<TurnoDelUsuario> turnos, Map<String, Integer> d) {
        List<Observacion> notadas = new ArrayList<>();
        int mitad = turnos.size() / 2;
        double palabrasAlPrincipio = mediana(turnos.subList(0, mitad).stream()
                .mapToDouble(TurnoDelUsuario::wordCount).toArray());
        double palabrasAlFinal = mediana(turnos.subList(mitad, turnos.size()).stream()
                .mapToDouble(TurnoDelUsuario::wordCount).toArray());

        if (d.get("arranque") >= NOTABLE_ALTO) {
            notadas.add(Observacion.STEADY_START);
        }
        // El guion pone el terreno cómodo al principio: si ahí las frases se alargaron solas, es
        // que tiene más idioma del que usa cuando se pone nervioso.
        if (palabrasAlPrincipio >= 20 && palabrasAlPrincipio >= palabrasAlFinal * 1.5) {
            notadas.add(Observacion.LONG_ANSWERS_WHEN_COMFORTABLE);
        }
        // Y el empuje suave va al final: no encogerse ahí es lo contrario de lo anterior.
        if (palabrasAlFinal >= palabrasAlPrincipio && d.get("extension") >= NOTABLE_BAJO) {
            notadas.add(Observacion.HOLDS_UNDER_PRESSURE);
        }

        if (d.get("arranque") <= NOTABLE_BAJO) {
            notadas.add(Observacion.SLOW_START);
        }
        if (d.get("continuidad") <= NOTABLE_BAJO) {
            notadas.add(Observacion.ABANDONS_CLAUSES);
        }
        if (d.get("extension") <= NOTABLE_BAJO) {
            notadas.add(Observacion.SHORT_ANSWERS);
        }
        if (d.get("autonomia") <= NOTABLE_BAJO) {
            notadas.add(Observacion.RETREATS_TO_NATIVE);
        }
        if (d.get("soltura") <= NOTABLE_BAJO) {
            notadas.add(Observacion.FILLER_HEAVY);
        }
        return List.copyOf(notadas);
    }

    /** Recta entre dos anclas, recortada a 0–100. El ancla del 100 puede ser la menor o la mayor. */
    private static int normalizar(double valor, double anclaDe100, double anclaDe0) {
        if (anclaDe100 == anclaDe0) {
            return 100;
        }
        double t = (valor - anclaDe0) / (anclaDe100 - anclaDe0);
        return (int) Math.round(Math.max(0, Math.min(1, t)) * 100);
    }

    private static double medianaDeLatencia(List<TurnoDelUsuario> turnos) {
        return mediana(turnos.stream().mapToDouble(TurnoDelUsuario::latencyMs).toArray());
    }

    private static double medianaDePalabras(List<TurnoDelUsuario> turnos) {
        return mediana(turnos.stream().mapToDouble(TurnoDelUsuario::wordCount).toArray());
    }

    private static double proporcionDeTurnosCon(List<TurnoDelUsuario> turnos,
                                                java.util.function.Predicate<TurnoDelUsuario> cumple) {
        return (double) turnos.stream().filter(cumple).count() / turnos.size();
    }

    /**
     * Autocorrecciones y muletillas por cada cien palabras. Se cuentan juntas porque son la misma
     * cosa vista dos veces: los tropiezos de quien está construyendo la frase mientras la dice.
     */
    private static double tropiezosPorCienPalabras(List<TurnoDelUsuario> turnos) {
        int palabras = turnos.stream().mapToInt(TurnoDelUsuario::wordCount).sum();
        int tropiezos = turnos.stream()
                .mapToInt(t -> t.selfCorrections() + t.fillerCount()).sum();
        if (palabras == 0) {
            return SOLTURA_0_POR_CIEN;   // sin palabras no hay soltura que reconocer
        }
        return tropiezos * 100.0 / palabras;
    }

    private static double mediana(double[] valores) {
        if (valores.length == 0) {
            return 0;
        }
        double[] ordenados = valores.clone();
        java.util.Arrays.sort(ordenados);
        int medio = ordenados.length / 2;
        return ordenados.length % 2 == 1
                ? ordenados[medio]
                : (ordenados[medio - 1] + ordenados[medio]) / 2.0;
    }
}
