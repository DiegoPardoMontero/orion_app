package co.orion.reputation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Convierte el desempeño de un profesor en el número que ordena el buscador. Clase pura: sin
 * Spring, sin repositorios y sin reloj — como {@code SlotCalculator}, y por la misma razón: es la
 * pieza cuyo comportamiento hay que poder verificar caso por caso sin levantar nada.
 *
 * <h2>El arranque en frío, que importa más que la fórmula</h2>
 *
 * Un profesor recién aprobado no tiene clases, así que puntúa cero, así que aparece último, así que
 * nadie lo reserva, así que sigue sin tener clases. Es una trampa cerrada, y quien la paga es la
 * oferta: la academia se queda con los mismos tres profesores de siempre.
 *
 * La salida es darle a quien tiene poco historial el puntaje MEDIANO del grupo —ni premio ni
 * castigo, "todavía no sabemos"— con una pequeña variación estable derivada de su id, para que el
 * orden entre novatos rote en vez de quedar fijo por azar alfabético. Estable dentro de una misma
 * corrida: el mismo id produce siempre la misma variación, así que dos cálculos seguidos no barajan
 * la portada.
 */
public final class RankingCalculator {

    /** Clases a partir de las cuales el desempeño ya dice algo. Por debajo, puntaje neutro. */
    private final int coldStartLessons;

    /** Con cuántas clases se considera "saturado" el aporte del volumen: más allá no suma. */
    private static final int LESSONS_SATURATION = 20;

    /** Con cuántos estudiantes distintos se satura la retención. */
    private static final int RETENTION_SATURATION = 10;

    /** Amplitud de la variación entre novatos, en puntos del score (0–100). */
    private static final double COLD_START_JITTER = 4.0;

    public RankingCalculator(int coldStartLessons) {
        this.coldStartLessons = coldStartLessons;
    }

    /**
     * Puntúa a todo el grupo de una vez. Tiene que ser en grupo y no uno a uno porque el puntaje
     * neutro de los novatos es la mediana de los demás: depende del conjunto.
     */
    public Map<UUID, BigDecimal> scoreAll(List<RankingInputs> all, RankingWeights weights) {
        Map<UUID, BigDecimal> scores = new HashMap<>();

        List<RankingInputs> established = all.stream()
                .filter(p -> p.lessonsCompleted() >= coldStartLessons)
                .toList();

        List<Double> establishedScores = new ArrayList<>();
        for (RankingInputs professor : established) {
            double score = merit(professor, weights);
            establishedScores.add(score);
            scores.put(professor.professorId(), round(penalise(score, professor, weights)));
        }

        double neutral = median(establishedScores);
        for (RankingInputs professor : all) {
            if (professor.lessonsCompleted() >= coldStartLessons) {
                continue;
            }
            double score = neutral + jitter(professor.professorId());
            scores.put(professor.professorId(), round(penalise(score, professor, weights)));
        }
        return scores;
    }

    /** El mérito bruto, en una escala de 0 a 100. */
    private double merit(RankingInputs p, RankingWeights w) {
        double total = w.rating() + w.attendance() + w.response()
                + w.lessons() + w.completeness() + w.retention();
        if (total <= 0) {
            return 0;
        }
        double weighted = w.rating() * ratingNorm(p)
                + w.attendance() * rateNorm(p.attendanceRate())
                + w.response() * rateNorm(p.responseRate())
                + w.lessons() * saturating(p.lessonsCompleted(), LESSONS_SATURATION)
                + w.completeness() * clamp(p.profileCompleteness() / 100.0)
                + w.retention() * saturating(p.activeStudents(), RETENTION_SATURATION);
        return (weighted / total) * 100.0;
    }

    /**
     * Una sanción activa pesa sobre la visibilidad, que es exactamente su propósito: no castigar
     * por castigar, sino dejar de recomendar a quien no está cumpliendo.
     */
    private double penalise(double score, RankingInputs p, RankingWeights w) {
        return Math.max(0, score - (double) p.activeSanctions() * w.sanctionPenaltyPoints());
    }

    /**
     * 1–5 estrellas a 0–1. Sin reseñas devuelve 0,5: la ausencia de opinión no es una mala opinión,
     * y hundir por ello a un profesor nuevo es la misma trampa del arranque en frío por otra vía.
     */
    private double ratingNorm(RankingInputs p) {
        if (p.ratingAvg() == null || p.ratingCount() == 0) {
            return 0.5;
        }
        return clamp((p.ratingAvg().doubleValue() - 1.0) / 4.0);
    }

    /** Una tasa 0–100 a 0–1. Null (nada que medir todavía) vale 0,5, por lo mismo. */
    private double rateNorm(BigDecimal rate) {
        return rate == null ? 0.5 : clamp(rate.doubleValue() / 100.0);
    }

    /** Crece rápido al principio y se aplana: la clase 21 no debe valer lo mismo que la 2.ª. */
    private double saturating(int value, int saturation) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(1.0, (double) value / saturation);
    }

    /**
     * Variación estable a partir del id. Determinista —el mismo profesor obtiene siempre la misma—
     * para que dos corridas seguidas no le cambien el orden a la portada, pero suficiente para que
     * los novatos no queden ordenados por un criterio invisible y siempre el mismo.
     */
    private double jitter(UUID professorId) {
        int hash = professorId.hashCode();
        double normalised = (double) (Math.floorMod(hash, 1000)) / 1000.0;   // 0..1
        return (normalised - 0.5) * 2 * COLD_START_JITTER;
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) {
            return 50.0;   // nadie establecido todavía: el medio de la escala
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
