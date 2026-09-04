package co.orion.scheduling.domain;

import co.orion.shared.time.BusinessZone;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * El corazón del MVP: convierte reglas semanales, bloqueos y franjas ocupadas en cupos concretos.
 *
 * Clase pura: sin Spring, sin repositorios, sin reloj del sistema. Todo lo que necesita entra por
 * parámetro — incluido el "ahora" —, así que se puede probar exhaustivamente en milisegundos.
 */
public final class SlotCalculator {

    public static final ZoneId BOGOTA = BusinessZone.BOGOTA;

    private static final Duration SLOT_LENGTH = Duration.ofHours(1);

    public List<Slot> calculate(List<AvailabilityRule> rules,
                                List<AvailabilityException> exceptions,
                                List<OccupiedInterval> occupied,
                                LocalDate from,
                                LocalDate to,
                                ZonedDateTime now) {
        List<Slot> slots = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<AvailabilityException> dayExceptions = exceptionsOn(exceptions, date);
            if (dayExceptions.stream().anyMatch(AvailabilityException::isWholeDay)) {
                continue;
            }
            for (AvailabilityRule rule : rules) {
                if (!rule.isActive() || rule.getWeekday() != date.getDayOfWeek()) {
                    continue;
                }
                addSlotsOf(rule, date, dayExceptions, occupied, now, slots);
            }
        }

        slots.sort(Comparator.comparing(slot -> slot.startsAt().toInstant()));
        return slots;
    }

    private void addSlotsOf(AvailabilityRule rule,
                            LocalDate date,
                            List<AvailabilityException> dayExceptions,
                            List<OccupiedInterval> occupied,
                            ZonedDateTime now,
                            List<Slot> slots) {
        ZonedDateTime ruleEnd = date.atTime(rule.getEndTime()).atZone(BOGOTA);

        // Los cupos empiezan donde empieza la regla y avanzan de hora en hora mientras quepan
        // enteros: una regla 18:00–21:00 da 18:00, 19:00 y 20:00, nunca un cupo a medias.
        for (ZonedDateTime start = date.atTime(rule.getStartTime()).atZone(BOGOTA);
             !start.plus(SLOT_LENGTH).isAfter(ruleEnd);
             start = start.plus(SLOT_LENGTH)) {

            ZonedDateTime end = start.plus(SLOT_LENGTH);

            if (!start.isAfter(now)) {
                continue; // futuro estricto: un cupo que ya empezó no se ofrece
            }
            if (blockedByException(start, end, date, dayExceptions)) {
                continue;
            }
            if (alreadyOccupied(start, end, occupied)) {
                continue;
            }
            slots.add(new Slot(start, end));
        }
    }

    private List<AvailabilityException> exceptionsOn(List<AvailabilityException> exceptions, LocalDate date) {
        return exceptions.stream()
                .filter(exception -> exception.getExceptionDate().equals(date))
                .toList();
    }

    private boolean blockedByException(ZonedDateTime slotStart,
                                       ZonedDateTime slotEnd,
                                       LocalDate date,
                                       List<AvailabilityException> dayExceptions) {
        return dayExceptions.stream()
                .filter(exception -> !exception.isWholeDay())
                .anyMatch(exception -> intersects(
                        slotStart, slotEnd,
                        date.atTime(exception.getStartTime()).atZone(BOGOTA),
                        date.atTime(exception.getEndTime()).atZone(BOGOTA)));
    }

    private boolean alreadyOccupied(ZonedDateTime slotStart,
                                    ZonedDateTime slotEnd,
                                    List<OccupiedInterval> occupied) {
        return occupied.stream()
                .anyMatch(interval -> intersects(slotStart, slotEnd, interval.start(), interval.end()));
    }

    /**
     * Semántica semiabierta [inicio, fin): se intersectan si aInicio < bFin && bInicio < aFin.
     * Por eso una excepción 10:00–11:00 mata el cupo de las 10:00 pero no el de las 11:00,
     * que empieza justo donde la excepción termina.
     */
    private boolean intersects(ZonedDateTime aStart, ZonedDateTime aEnd,
                               ZonedDateTime bStart, ZonedDateTime bEnd) {
        Instant a1 = aStart.toInstant();
        Instant a2 = aEnd.toInstant();
        Instant b1 = bStart.toInstant();
        Instant b2 = bEnd.toInstant();
        return a1.isBefore(b2) && b1.isBefore(a2);
    }
}
