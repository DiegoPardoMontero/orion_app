package co.orion.scheduling.domain;

import static co.orion.scheduling.domain.SlotCalculator.BOGOTA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Contrato del algoritmo de cupos (casos C1–C10 del brief de la Tarea 2).
 *
 * Tests puros: sin contexto de Spring y sin base de datos, corren en milisegundos. Esa velocidad
 * no es casualidad — es la consecuencia directa de que SlotCalculator no dependa de nada.
 *
 * Fechas de referencia: 2026-07-13 es lunes, 2026-07-15 miércoles, 2026-07-20 el lunes siguiente.
 */
class SlotCalculatorTest {

    private static final UUID PROFESSOR = UUID.randomUUID();

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 13);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 7, 20);

    /** Un "ahora" muy anterior al rango, para que el filtro de pasado no interfiera. */
    private static final ZonedDateTime LONG_BEFORE = ZonedDateTime.of(
            LocalDate.of(2026, 7, 1), LocalTime.of(0, 0), BOGOTA);

    private final SlotCalculator calculator = new SlotCalculator();

    // C1
    @Test
    void aSingleRuleProducesOneSlotEveryHalfHourInsideIt() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(), List.of(),
                MONDAY, MONDAY, LONG_BEFORE);

        // 18:00–21:00 da cinco arranques, de media en media hora. Las 20:30 no: esa clase
        // terminaría a las 21:25 y la franja cierra a las 21:00.
        assertThat(startTimes(slots)).containsExactly(
                bogota(MONDAY, 18), bogota(MONDAY, 18, 30), bogota(MONDAY, 19),
                bogota(MONDAY, 19, 30), bogota(MONDAY, 20));
    }

    // C1 (segunda mitad: nada en los demás días del rango)
    @Test
    void aRuleOnlyProducesSlotsOnItsOwnWeekday() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(), List.of(),
                MONDAY, MONDAY.plusDays(5), LONG_BEFORE);

        assertThat(slots).hasSize(5);
        assertThat(slots).allSatisfy(slot ->
                assertThat(slot.startsAt().toLocalDate()).isEqualTo(MONDAY));
    }

    // C2
    @Test
    void twoRulesOnTheSameWeekdayProduceSlotsFromBoth() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.WEDNESDAY, 8, 10), rule(DayOfWeek.WEDNESDAY, 15, 17)),
                List.of(), List.of(),
                WEDNESDAY, WEDNESDAY, LONG_BEFORE);

        assertThat(startTimes(slots)).containsExactly(
                bogota(WEDNESDAY, 8), bogota(WEDNESDAY, 8, 30), bogota(WEDNESDAY, 9),
                bogota(WEDNESDAY, 15), bogota(WEDNESDAY, 15, 30), bogota(WEDNESDAY, 16));
    }

    // C3
    @Test
    void aWholeDayExceptionRemovesEverySlotOfThatDateOnly() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(AvailabilityException.wholeDay(PROFESSOR, MONDAY, "Festivo")),
                List.of(),
                MONDAY, NEXT_MONDAY, LONG_BEFORE);

        // El lunes bloqueado desaparece entero; el lunes siguiente queda intacto.
        assertThat(startTimes(slots)).containsExactly(
                bogota(NEXT_MONDAY, 18), bogota(NEXT_MONDAY, 18, 30), bogota(NEXT_MONDAY, 19),
                bogota(NEXT_MONDAY, 19, 30), bogota(NEXT_MONDAY, 20));
    }

    // C4
    @Test
    void aPartialExceptionRemovesOnlyTheSlotItExactlyCovers() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.WEDNESDAY, 9, 12)),
                List.of(partialException(WEDNESDAY, 10, 0, 11, 0)),
                List.of(),
                WEDNESDAY, WEDNESDAY, LONG_BEFORE);

        // Semiabierto: el cupo de las 11:00 empieza justo cuando la excepción termina, así que vive.
        assertThat(startTimes(slots)).containsExactly(
                bogota(WEDNESDAY, 9), bogota(WEDNESDAY, 11));
    }

    // C5
    @Test
    void aPartialExceptionThatStraddlesTwoSlotsRemovesBoth() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.WEDNESDAY, 9, 13)),
                List.of(partialException(WEDNESDAY, 10, 30, 11, 30)),
                List.of(),
                WEDNESDAY, WEDNESDAY, LONG_BEFORE);

        // 10:30–11:30 se lleva por delante los arranques de 10:00, 10:30 y 11:00. Las 9:30
        // sobreviven por cinco minutos —terminan a las 10:25— y las 11:30 empiezan justo donde la
        // excepción acaba, que en semiabierto no cuenta como choque.
        assertThat(startTimes(slots)).containsExactly(
                bogota(WEDNESDAY, 9), bogota(WEDNESDAY, 9, 30),
                bogota(WEDNESDAY, 11, 30), bogota(WEDNESDAY, 12));
    }

    // C6
    @Test
    void anOccupiedIntervalRemovesTheSlotItOverlaps() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(),
                List.of(new OccupiedInterval(bogota(MONDAY, 19), bogota(MONDAY, 20))),
                MONDAY, MONDAY, LONG_BEFORE);

        // La resta de reservas funciona hoy, con datos sintéticos: la Tarea 3 solo la conectará.
        assertThat(startTimes(slots)).containsExactly(
                bogota(MONDAY, 18), bogota(MONDAY, 20));
    }

    // C7
    @Test
    void slotsThatAlreadyStartedAreNotOffered() {
        ZonedDateTime now = ZonedDateTime.of(WEDNESDAY, LocalTime.of(10, 30), BOGOTA);

        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.WEDNESDAY, 9, 12)),
                List.of(), List.of(),
                WEDNESDAY, WEDNESDAY, now);

        // A las 10:30 ya se fueron el de las 9 y el de las 10 (que está en curso). Queda el de las 11.
        assertThat(startTimes(slots)).containsExactly(bogota(WEDNESDAY, 11));
    }

    // C8
    @Test
    void anInactiveRuleProducesNoSlots() {
        AvailabilityRule inactive = rule(DayOfWeek.MONDAY, 18, 21);
        inactive.deactivate();

        List<Slot> slots = calculator.calculate(
                List.of(inactive), List.of(), List.of(),
                MONDAY, MONDAY, LONG_BEFORE);

        assertThat(slots).isEmpty();
    }

    // C9
    @Test
    void theCalculationUsesBogotaTimeAndNotUtc() {
        // 2026-07-15T04:30Z son las 23:30 del 14 de julio en Bogotá: en UTC ya es miércoles,
        // pero para el profesor todavía es martes. Los cupos del miércoles deben salir completos.
        ZonedDateTime now = Instant.parse("2026-07-15T04:30:00Z").atZone(BOGOTA);

        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.WEDNESDAY, 8, 10)),
                List.of(), List.of(),
                WEDNESDAY, WEDNESDAY, now);

        assertThat(startTimes(slots)).containsExactly(
                bogota(WEDNESDAY, 8), bogota(WEDNESDAY, 8, 30), bogota(WEDNESDAY, 9));
        // Y con el offset correcto de Colombia, que no tiene horario de verano.
        assertThat(slots.getFirst().startsAt().getOffset().getId()).isEqualTo("-05:00");
    }

    // C10
    @Test
    void aSingleDayRangeOnlyProducesSlotsOfThatDay() {
        AvailabilityRule everyWednesday = rule(DayOfWeek.WEDNESDAY, 8, 10);

        List<Slot> slots = calculator.calculate(
                List.of(everyWednesday), List.of(), List.of(),
                WEDNESDAY, WEDNESDAY, LONG_BEFORE);

        assertThat(slots).hasSize(3);
        assertThat(slots).allSatisfy(slot ->
                assertThat(slot.startsAt().toLocalDate()).isEqualTo(WEDNESDAY));
    }

    /**
     * La clase sigue durando 55 minutos; lo que cambió es cada cuánto puede empezar. Antes solo en
     * punto, ahora también y media — sin acortar la clase y sin tocar el precio.
     */
    @Test
    void everySlotLastsFiftyFiveMinutesAndStartsOnTheHourOrTheHalf() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(), List.of(),
                MONDAY, MONDAY, LONG_BEFORE);

        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.endsAt()).isEqualTo(slot.startsAt().plusMinutes(55));
            assertThat(slot.startsAt().getMinute()).isIn(0, 30);
        });
        assertThat(slots).hasSize(5);
    }

    /**
     * Los arranques que se ofrecen se pisan entre sí, y eso es correcto: son alternativas, no una
     * agenda. Tomar uno retira del listado los que se solapan con él — lo comprueba
     * {@link #reservarUnCupoRetiraElDeMediaHoraDespuesPeroNoElDeLaHoraSiguiente()}.
     *
     * <p>Lo que sí se conserva es el respiro del profesor: dos clases consecutivas de verdad —una
     * hora de diferencia— siguen dejando cinco minutos entre el fin de una y el inicio de la otra.
     */
    @Test
    void twoClassesAnHourApartStillLeaveTheFiveMinuteBreather() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(), List.of(),
                MONDAY, MONDAY, LONG_BEFORE);

        Slot lasSeis = slots.getFirst();
        Slot lasSiete = slots.stream()
                .filter(s -> s.startsAt().equals(lasSeis.startsAt().plusHours(1)))
                .findFirst().orElseThrow();

        assertThat(java.time.Duration.between(lasSeis.endsAt(), lasSiete.startsAt()))
                .isEqualTo(java.time.Duration.ofMinutes(5));
    }

    /**
     * La consecuencia de la media hora que hay que entender: un cupo tomado se lleva por delante al
     * de treinta minutos después, porque la clase dura 55. El de la hora siguiente sigue libre.
     */
    @Test
    void reservarUnCupoRetiraElDeMediaHoraDespuesPeroNoElDeLaHoraSiguiente() {
        List<Slot> slots = calculator.calculate(
                List.of(rule(DayOfWeek.MONDAY, 18, 21)),
                List.of(),
                List.of(new OccupiedInterval(bogota(MONDAY, 18), bogota(MONDAY, 18, 55))),
                MONDAY, MONDAY, LONG_BEFORE);

        assertThat(startTimes(slots)).containsExactly(
                bogota(MONDAY, 19), bogota(MONDAY, 19, 30), bogota(MONDAY, 20));
    }

    private AvailabilityRule rule(DayOfWeek weekday, int startHour, int endHour) {
        return new AvailabilityRule(PROFESSOR, weekday,
                LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private AvailabilityException partialException(LocalDate date,
                                                   int startHour, int startMinute,
                                                   int endHour, int endMinute) {
        return AvailabilityException.partial(PROFESSOR, date,
                LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute), null);
    }

    private ZonedDateTime bogota(LocalDate date, int hour) {
        return bogota(date, hour, 0);
    }

    private ZonedDateTime bogota(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), BOGOTA);
    }

    private List<ZonedDateTime> startTimes(List<Slot> slots) {
        return slots.stream().map(Slot::startsAt).toList();
    }
}
