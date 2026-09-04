package co.orion.scheduling.domain;

import co.orion.shared.time.BusinessZone;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Lo que un estudiante ha recorrido, calculado a partir de sus reservas. Clase pura, como
 * {@code SlotCalculator}: sin Spring, sin repositorios y sin reloj del sistema —el "ahora" entra
 * por parámetro—, para poder probar a fondo las rachas, que es donde está toda la aritmética
 * incómoda. No mover esta lógica a SQL.
 *
 * <p>Todo se razona en {@link BusinessZone#BOGOTA}: una clase del lunes a las 7 de la noche en
 * Bogotá pertenece a esa semana, no a la del servidor.
 */
public final class LearningProgress {

    /**
     * Qué cuenta como clase tomada. COMPLETED es la clase cerrada; CONFIRMED ya pasada es la que
     * ocurrió pero que el cierre automático todavía no ha tocado —hasta 24 horas después—, y para
     * el estudiante ya fue una clase.
     *
     * <p>Los dos no-show quedan fuera a propósito: si faltó él, no la tomó; si faltó el profesor,
     * no la hubo. Contarlas inflaría el número justo con las veces que algo salió mal.
     */
    public static boolean cuentaComoTomada(BookingStatus status, Instant endsAt, Instant ahora) {
        if (status == BookingStatus.COMPLETED) {
            return true;
        }
        return status == BookingStatus.CONFIRMED && !endsAt.isAfter(ahora);
    }

    /** Una clase ya tomada, reducida a lo que necesitan los cálculos. */
    public record Tomada(UUID professorId, Instant startsAt, Instant endsAt) {

        LocalDate dia() {
            return startsAt.atZone(BusinessZone.BOGOTA).toLocalDate();
        }

        /** El lunes de su semana: la clave con la que se agrupan las rachas. */
        LocalDate semana() {
            LocalDate d = dia();
            return d.minusDays(d.getDayOfWeek().getValue() - 1L);
        }

        long minutos() {
            return ChronoUnit.MINUTES.between(startsAt, endsAt);
        }
    }

    public record Rachas(int actual, int mejor) {
    }

    private LearningProgress() {
    }

    /** Minutos sumados de verdad, no clases × 60: si algún día hay clases de otra duración, cuadra. */
    public static long minutosTotales(List<Tomada> tomadas) {
        return tomadas.stream().mapToLong(Tomada::minutos).sum();
    }

    /**
     * Semanas seguidas con al menos una clase.
     *
     * <p>La racha ACTUAL sigue viva si la última semana con clase es esta o la pasada. Que cuente la
     * pasada no es un descuido: alguien que da clase los martes y mira su panel un lunes no ha roto
     * nada todavía, y decirle que su racha se cortó sería mentirle. Se rompe al terminar la semana
     * siguiente sin clase.
     */
    public static Rachas rachas(List<Tomada> tomadas, Instant ahora) {
        if (tomadas.isEmpty()) {
            return new Rachas(0, 0);
        }

        SortedSet<LocalDate> semanas = new TreeSet<>();
        for (Tomada tomada : tomadas) {
            semanas.add(tomada.semana());
        }

        int mejor = 1;
        int corrida = 1;
        LocalDate anterior = null;
        for (LocalDate semana : semanas) {
            if (anterior != null) {
                corrida = semana.equals(anterior.plusWeeks(1)) ? corrida + 1 : 1;
            }
            mejor = Math.max(mejor, corrida);
            anterior = semana;
        }

        LocalDate hoy = ahora.atZone(BusinessZone.BOGOTA).toLocalDate();
        LocalDate estaSemana = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        LocalDate ultima = semanas.last();
        boolean viva = ultima.equals(estaSemana) || ultima.equals(estaSemana.minusWeeks(1));

        return new Rachas(viva ? corrida : 0, mejor);
    }

    /**
     * Clases por día, para el mapa del año. Solo los días CON clase: un año son 365 entradas y 350
     * serían ceros que la interfaz ya sabe dibujar sin que se los manden.
     */
    public static Map<LocalDate, Integer> porDia(List<Tomada> tomadas, LocalDate desde) {
        Map<LocalDate, Integer> mapa = new LinkedHashMap<>();
        tomadas.stream()
                .map(Tomada::dia)
                .filter(dia -> !dia.isBefore(desde))
                .sorted()
                .forEach(dia -> mapa.merge(dia, 1, Integer::sum));
        return mapa;
    }
}
