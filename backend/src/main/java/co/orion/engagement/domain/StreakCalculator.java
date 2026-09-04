package co.orion.engagement.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import co.orion.scheduling.domain.LearningProgress.Tomada;
import co.orion.shared.time.BusinessZone;

/**
 * La racha semanal con protección.
 *
 * <p>Se apoya en la definición de semana de {@code LearningProgress} en vez de escribir la suya:
 * dos definiciones de racha en el mismo producto es un bug esperando. Lo que añade aquí es la
 * <strong>protección</strong>, que es una regla de gamificación y no tiene por qué vivir en
 * {@code scheduling}.
 *
 * <p>Clase pura, como {@code SlotCalculator}: sin Spring, sin repositorios y con el «ahora» por
 * parámetro. Es lo que permite probar a fondo los casos incómodos —el cambio de mes, dos semanas
 * vacías seguidas, el corte del lunes— con datos en memoria.
 *
 * <p><strong>La protección no se concede aquí.</strong> Este cálculo dice qué semanas necesitarían
 * una, y quien lo llama las persiste. Así el recálculo produce exactamente el mismo estado que el
 * procesamiento incremental, que es lo que protege todo el bloque.
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /**
     * @param actual semanas seguidas hasta hoy; 0 si la racha se cortó
     * @param mejor la mejor marca histórica, que no se borra al perder la actual
     * @param semanasProtegidas los lunes de las semanas vacías que la protección salvó
     */
    public record Racha(int actual, int mejor, List<LocalDate> semanasProtegidas) {
    }

    /** El lunes de la semana de una fecha. */
    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - 1L);
    }

    /**
     * Calcula la racha.
     *
     * @param tomadas las clases que cuentan
     * @param mesesYaProtegidos meses (su día 1) en los que el estudiante ya gastó su protección
     * @param ahora el instante de referencia
     */
    public static Racha calcular(List<Tomada> tomadas, Set<LocalDate> mesesYaProtegidos,
                                 Instant ahora) {
        if (tomadas.isEmpty()) {
            return new Racha(0, 0, List.of());
        }

        SortedSet<LocalDate> conClase = new TreeSet<>();
        for (Tomada tomada : tomadas) {
            conClase.add(tomada.semana());
        }

        LocalDate hoy = ahora.atZone(BusinessZone.BOGOTA).toLocalDate();
        LocalDate estaSemana = lunesDe(hoy);
        LocalDate primera = conClase.first();

        // Los meses gastados se van acumulando sobre los que ya venían de la base: dentro de un
        // mismo recorrido tampoco se puede usar dos veces el mismo mes.
        Set<LocalDate> mesesGastados = new TreeSet<>(mesesYaProtegidos);
        List<LocalDate> protegidasAhora = new ArrayList<>();

        int corrida = 0;
        int mejor = 0;
        boolean anteriorVacia = false;

        for (LocalDate semana = primera; !semana.isAfter(estaSemana); semana = semana.plusWeeks(1)) {
            if (conClase.contains(semana)) {
                corrida++;
                mejor = Math.max(mejor, corrida);
                anteriorVacia = false;
                continue;
            }

            // La semana en curso todavía no ha terminado: que aún no tenga clase no la rompe.
            if (semana.equals(estaSemana)) {
                break;
            }

            // Una semana protegida PUENTEA la racha pero no suma: no hubo clase, y contarla sería
            // contar una ausencia como si fuera una clase. El copy del diseño lo prohíbe, y con
            // razón: la cifra tiene que seguir significando «semanas en las que practiqué».
            LocalDate mes = semana.withDayOfMonth(1);
            boolean puedeProteger = !anteriorVacia && !mesesGastados.contains(mes);
            if (puedeProteger) {
                mesesGastados.add(mes);
                protegidasAhora.add(semana);
                anteriorVacia = true;   // dos vacías seguidas cortan, aunque hubiera protección
                continue;
            }

            corrida = 0;
            anteriorVacia = true;
        }

        return new Racha(corrida, mejor, List.copyOf(protegidasAhora));
    }

    /**
     * Las últimas {@code semanas} semanas con su estado, para el mapa de constancia. La más
     * reciente va al final, que es como se lee una línea de tiempo.
     */
    public static List<SemanaDelMapa> mapa(List<Tomada> tomadas, Set<LocalDate> protegidas,
                                           int semanas, Instant ahora) {
        SortedSet<LocalDate> conClase = new TreeSet<>();
        for (Tomada tomada : tomadas) {
            conClase.add(tomada.semana());
        }

        LocalDate estaSemana = lunesDe(ahora.atZone(BusinessZone.BOGOTA).toLocalDate());
        List<SemanaDelMapa> resultado = new ArrayList<>();
        for (int i = semanas - 1; i >= 0; i--) {
            LocalDate lunes = estaSemana.minusWeeks(i);
            EstadoSemana estado;
            if (conClase.contains(lunes)) {
                estado = EstadoSemana.CUMPLIDA;
            } else if (protegidas.contains(lunes)) {
                estado = EstadoSemana.PROTEGIDA;
            } else if (lunes.equals(estaSemana)) {
                estado = EstadoSemana.EN_CURSO;
            } else {
                estado = EstadoSemana.VACIA;
            }
            resultado.add(new SemanaDelMapa(lunes, estado));
        }
        return resultado;
    }

    public record SemanaDelMapa(LocalDate weekStart, EstadoSemana estado) {
    }

    /** Los estados del mapa de constancia (§2g del diseño). */
    public enum EstadoSemana {
        CUMPLIDA,
        EN_CURSO,
        PROTEGIDA,
        VACIA
    }
}
