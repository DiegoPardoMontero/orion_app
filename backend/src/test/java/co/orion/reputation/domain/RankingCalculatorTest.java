package co.orion.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * El puntaje que ordena el buscador, verificado sin levantar nada. Lo que más importa aquí no es la
 * fórmula —que es una hipótesis y cambiará— sino el arranque en frío: si un profesor nuevo queda
 * último para siempre, nunca recibe su primera reserva y la oferta de la academia se congela.
 */
class RankingCalculatorTest {

    private final RankingCalculator calculator = new RankingCalculator(5);
    private final RankingWeights weights = RankingWeights.defaults();

    private RankingInputs professor(String rating, int lessons, String attendance, String response,
                                    int completeness, int students, int sanctions) {
        return new RankingInputs(UUID.randomUUID(),
                rating == null ? null : new BigDecimal(rating),
                rating == null ? 0 : 10,
                lessons,
                attendance == null ? null : new BigDecimal(attendance),
                response == null ? null : new BigDecimal(response),
                completeness, students, sanctions);
    }

    @Test
    void elProfesorImpecablePuntuaAlMaximo() {
        RankingInputs impecable = professor("5.00", 40, "100.00", "100.00", 100, 20, 0);

        BigDecimal score = calculator.scoreAll(List.of(impecable), weights).get(impecable.professorId());

        assertThat(score.doubleValue()).isEqualTo(100.0);
    }

    @Test
    void faltarAClasesHundeElPuntaje() {
        RankingInputs cumplidor = professor("4.50", 30, "100.00", "90.00", 100, 10, 0);
        RankingInputs ausente = professor("4.50", 30, "40.00", "90.00", 100, 10, 0);

        Map<UUID, BigDecimal> scores = calculator.scoreAll(List.of(cumplidor, ausente), weights);

        assertThat(scores.get(cumplidor.professorId()))
                .isGreaterThan(scores.get(ausente.professorId()));
    }

    /** El corazón del bloque: sin esto, el profesor nuevo nunca recibe su primera reserva. */
    @Test
    void unProfesorSinClasesNoQuedaUltimoParaSiempre() {
        RankingInputs bueno = professor("5.00", 40, "100.00", "100.00", 100, 15, 0);
        RankingInputs mediocre = professor("3.00", 30, "60.00", "50.00", 60, 3, 0);
        RankingInputs nuevo = professor(null, 0, null, null, 80, 0, 0);

        Map<UUID, BigDecimal> scores = calculator.scoreAll(List.of(bueno, mediocre, nuevo), weights);

        // Ni el mejor ni el peor: recibe el puntaje mediano del grupo, que es exactamente la
        // verdad de su situación — todavía no se sabe nada de él.
        assertThat(scores.get(nuevo.professorId()))
                .isGreaterThan(scores.get(mediocre.professorId()))
                .isLessThan(scores.get(bueno.professorId()));
    }

    @Test
    void sinNadieEstablecidoTodosArrancanAlrededorDelMedio() {
        RankingInputs uno = professor(null, 0, null, null, 50, 0, 0);
        RankingInputs otro = professor(null, 1, null, null, 50, 0, 0);

        Map<UUID, BigDecimal> scores = calculator.scoreAll(List.of(uno, otro), weights);

        assertThat(scores.values()).allSatisfy(score ->
                assertThat(score.doubleValue()).isBetween(46.0, 54.0));
    }

    /** El desempate entre novatos rota, pero no baraja la portada en cada corrida. */
    @Test
    void elOrdenEntreNovatosEsEstableEntreCorridas() {
        List<RankingInputs> novatos = List.of(
                professor(null, 0, null, null, 70, 0, 0),
                professor(null, 1, null, null, 70, 0, 0),
                professor(null, 2, null, null, 70, 0, 0));

        Map<UUID, BigDecimal> primera = calculator.scoreAll(novatos, weights);
        Map<UUID, BigDecimal> segunda = calculator.scoreAll(novatos, weights);

        assertThat(primera).isEqualTo(segunda);
        // Y no todos empatan: el desempate existe de verdad.
        assertThat(primera.values().stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    void unaSancionActivaDescuentaVisibilidad() {
        RankingInputs limpio = professor("4.50", 30, "90.00", "90.00", 90, 8, 0);
        RankingInputs sancionado = professor("4.50", 30, "90.00", "90.00", 90, 8, 1);

        Map<UUID, BigDecimal> scores = calculator.scoreAll(List.of(limpio, sancionado), weights);

        assertThat(scores.get(limpio.professorId()).subtract(scores.get(sancionado.professorId())))
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void elPuntajeNuncaBajaDeCero() {
        RankingInputs hundido = professor("1.00", 10, "0.00", "0.00", 0, 0, 9);

        BigDecimal score = calculator.scoreAll(List.of(hundido), weights).get(hundido.professorId());

        assertThat(score.doubleValue()).isEqualTo(0.0);
    }

    /** Sin reseñas no es lo mismo que con malas reseñas: la ausencia de opinión no castiga. */
    @Test
    void noTenerResenasNoCuentaComoTenerlasMalas() {
        RankingInputs sinResenas = professor(null, 30, "90.00", "90.00", 90, 8, 0);
        RankingInputs conMalasResenas = professor("1.00", 30, "90.00", "90.00", 90, 8, 0);

        Map<UUID, BigDecimal> scores = calculator.scoreAll(List.of(sinResenas, conMalasResenas), weights);

        assertThat(scores.get(sinResenas.professorId()))
                .isGreaterThan(scores.get(conMalasResenas.professorId()));
    }
}
