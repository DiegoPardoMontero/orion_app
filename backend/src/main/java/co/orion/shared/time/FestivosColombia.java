package co.orion.shared.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Los festivos de Colombia de un año. Clase pura: se calculan, no se consultan.
 *
 * <p>Tres clases de festivo:
 * <ul>
 *   <li>Fijos: caen el día que caen (1 de enero, 1 de mayo, 20 de julio, 7 de agosto, 8 y 25 de
 *       diciembre).</li>
 *   <li>Trasladables (Ley 51 de 1983, «Ley Emiliani»): si no caen en lunes, pasan al lunes
 *       siguiente (Reyes, San José, San Pedro y San Pablo, Asunción, Día de la Raza, Todos los
 *       Santos, Independencia de Cartagena).</li>
 *   <li>Los de Semana Santa, que dependen de la Pascua: Jueves y Viernes Santo sin moverse; la
 *       Ascensión, el Corpus Christi y el Sagrado Corazón, trasladados al lunes.</li>
 * </ul>
 *
 * <p>Se usa solo para la fecha de pago de las liquidaciones (decisión de Pardo del 25/09/2026). Los
 * plazos legales ({@link PlazoLegal}) siguen sin festivos a propósito: así vencen antes, nunca
 * después.
 */
public final class FestivosColombia {

    private static final List<MonthDay> FIJOS = List.of(
            MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(7, 20), MonthDay.of(8, 7),
            MonthDay.of(12, 8), MonthDay.of(12, 25));

    private static final List<MonthDay> TRASLADABLES = List.of(
            MonthDay.of(1, 6), MonthDay.of(3, 19), MonthDay.of(6, 29), MonthDay.of(8, 15),
            MonthDay.of(10, 12), MonthDay.of(11, 1), MonthDay.of(11, 11));

    private FestivosColombia() {
    }

    public static Set<LocalDate> delAnio(int anio) {
        Set<LocalDate> festivos = new TreeSet<>();
        for (MonthDay fijo : FIJOS) {
            festivos.add(fijo.atYear(anio));
        }
        for (MonthDay trasladable : TRASLADABLES) {
            festivos.add(alLunes(trasladable.atYear(anio)));
        }
        LocalDate pascua = pascua(anio);
        festivos.add(pascua.minusDays(3));              // Jueves Santo
        festivos.add(pascua.minusDays(2));              // Viernes Santo
        festivos.add(alLunes(pascua.plusDays(39)));     // Ascensión del Señor
        festivos.add(alLunes(pascua.plusDays(60)));     // Corpus Christi
        festivos.add(alLunes(pascua.plusDays(68)));     // Sagrado Corazón
        return festivos;
    }

    public static boolean esFestivo(LocalDate dia) {
        return delAnio(dia.getYear()).contains(dia);
    }

    /** De lunes a viernes y no festivo. */
    public static boolean esHabil(LocalDate dia) {
        return dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY
                && !esFestivo(dia);
    }

    /**
     * El n-ésimo día hábil desde {@code desde}, contándolo a él si es hábil: con n = 1 es el mismo
     * día o el primer hábil que le sigue.
     */
    public static LocalDate enesimoHabilDesde(LocalDate desde, int n) {
        LocalDate dia = desde;
        int contados = 0;
        while (true) {
            if (esHabil(dia) && ++contados == n) {
                return dia;
            }
            dia = dia.plusDays(1);
        }
    }

    private static LocalDate alLunes(LocalDate dia) {
        int hastaElLunes = (DayOfWeek.MONDAY.getValue() - dia.getDayOfWeek().getValue() + 7) % 7;
        return dia.plusDays(hastaElLunes);
    }

    /** Domingo de Pascua (algoritmo anónimo gregoriano, de Meeus/Jones/Butcher). */
    static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
