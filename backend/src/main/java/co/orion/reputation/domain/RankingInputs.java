package co.orion.reputation.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que se sabe de un profesor para puntuarlo. Las tasas vienen en porcentaje (0–100) tal como se
 * guardan; el cálculo las normaliza. {@code ratingAvg} puede ser null: un profesor sin reseñas no
 * es un profesor malo, es uno del que todavía no se sabe.
 */
public record RankingInputs(UUID professorId,
                            BigDecimal ratingAvg,
                            int ratingCount,
                            int lessonsCompleted,
                            BigDecimal attendanceRate,
                            BigDecimal responseRate,
                            int profileCompleteness,
                            int activeStudents,
                            int activeSanctions) {
}
