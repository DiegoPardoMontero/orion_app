package co.orion.engagement.api;

import java.time.LocalDate;
import java.util.List;

import co.orion.engagement.domain.StreakCalculator;

/**
 * El mapa de constancia: doce semanas, una celda por semana.
 *
 * <p>Doce y no un año. Con una o dos clases por semana, una cuadrícula anual está vacía en un 98 %
 * y comunica abandono en vez de progreso — el mismo dato, contado en la ventana equivocada, dice
 * lo contrario de lo que es verdad.
 */
public record MyStreakResponse(List<Semana> weeks) {

    public record Semana(LocalDate weekStart, String status) {
    }

    public static MyStreakResponse from(List<StreakCalculator.SemanaDelMapa> semanas) {
        return new MyStreakResponse(semanas.stream()
                .map(s -> new Semana(s.weekStart(), s.estado().name()))
                .toList());
    }
}
