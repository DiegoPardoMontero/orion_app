package co.orion.billing.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import co.orion.shared.time.BusinessZone;
import co.orion.shared.time.FechasEnPalabras;
import co.orion.shared.time.FestivosColombia;

/**
 * El motor de las liquidaciones quincenales, sin Spring, sin base y sin reloj: el «ahora» y el corte
 * entran por parámetro, como en {@code SlotCalculator}. Así cada regla del brief se prueba sola.
 *
 * <p>Hay dos cortes al mes, en hora de Bogotá: a las 00:00 del día 16 (cierra la quincena del 1 al 15)
 * y a las 00:00 del día 1 (cierra la del 16 al último día del mes anterior). En un corte se liquida
 * cada clase cuyo plazo de reclamo ya venció y que no tiene un reclamo abierto; lo demás espera al
 * corte siguiente. La fecha de pago comprometida es el tercer día hábil, con festivos, desde el día
 * del corte.
 */
public final class PayoutCalculator {

    /** Cuántos días hábiles hay para pagar una liquidación después de su corte. */
    public static final int DIAS_HABILES_PARA_PAGAR = 3;

    private PayoutCalculator() {
    }

    /** Una clase (o cancelación tardía) con su dinero ya liberado al profe y todavía sin liquidar. */
    public record Candidate(UUID bookingId, UUID paymentId, PayoutLineKind kind, Instant classStart, Instant classEnd,
                            String studentLabel, long grossCop, int commissionRateBps, long commissionCop,
                            long netCop, boolean openDispute) {
    }

    /** Un ajuste esperando su corte: una devolución sobre algo ya pagado, o un arrastre. */
    public record PendingAdjustment(UUID adjustmentId, UUID bookingId, PayoutLineKind kind, long amountCop,
                                    String description) {
    }

    public record Line(PayoutLineKind kind, UUID bookingId, UUID paymentId, UUID adjustmentId, Instant classAt,
                       String studentLabel, long grossCop, Integer commissionRateBps, long commissionCop, long netCop,
                       String description) {
    }

    /** Lo que no entra en este corte, y por qué, dicho para el profe. */
    public record Deferred(Candidate candidate, String reason) {
    }

    public record Result(List<Line> lines, List<Deferred> deferred, long grossCop, long commissionCop,
                         long adjustmentsCop, long netCop) {
    }

    /** Una quincena: sus días y el instante de su corte. */
    public record Fortnight(LocalDate start, LocalDate end, Instant cutoff) {

        public LocalDate cutoffDate() {
            return cutoff.atZone(BusinessZone.BOGOTA).toLocalDate();
        }
    }

    /** La última quincena ya cerrada en {@code ahora}: la del corte más reciente. */
    public static Fortnight latestClosed(Instant ahora) {
        LocalDate hoy = ahora.atZone(BusinessZone.BOGOTA).toLocalDate();
        LocalDate diaDelCorte = hoy.getDayOfMonth() >= 16 ? hoy.withDayOfMonth(16) : hoy.withDayOfMonth(1);
        return closedBy(diaDelCorte);
    }

    /** La quincena que se cerrará en el próximo corte, estrictamente después de {@code ahora}. */
    public static Fortnight next(Instant ahora) {
        LocalDate hoy = ahora.atZone(BusinessZone.BOGOTA).toLocalDate();
        LocalDate diaDelCorte = hoy.getDayOfMonth() >= 16 ? hoy.withDayOfMonth(1).plusMonths(1) : hoy.withDayOfMonth(16);
        return closedBy(diaDelCorte);
    }

    /** La quincena que cierra el corte de ese día (un 1 o un 16). */
    public static Fortnight closedBy(LocalDate diaDelCorte) {
        Instant corte = diaDelCorte.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        if (diaDelCorte.getDayOfMonth() == 16) {
            return new Fortnight(diaDelCorte.withDayOfMonth(1), diaDelCorte.withDayOfMonth(15), corte);
        }
        if (diaDelCorte.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("Los cortes son el 1 y el 16: " + diaDelCorte);
        }
        LocalDate ultimoDelMesAnterior = diaDelCorte.minusDays(1);
        return new Fortnight(ultimoDelMesAnterior.withDayOfMonth(16), ultimoDelMesAnterior, corte);
    }

    /** A más tardar el tercer día hábil desde el día del corte, contando festivos de Colombia. */
    public static LocalDate committedPayDate(Fortnight quincena) {
        return FestivosColombia.enesimoHabilDesde(quincena.cutoffDate(), DIAS_HABILES_PARA_PAGAR);
    }

    /** Las líneas de la liquidación de un profe en un corte, lo que espera, y los totales. */
    public static Result calculate(List<Candidate> candidates, List<PendingAdjustment> adjustments, Instant cutoff,
                                   Duration claimWindow) {
        List<Line> lines = new ArrayList<>();
        List<Deferred> deferred = new ArrayList<>();
        for (Candidate c : candidates) {
            // Una clase de $0 (la prueba gratis) no genera línea: no hay nada que entregar.
            if (c.netCop() <= 0) {
                continue;
            }
            String espera = pendingReason(c, cutoff, claimWindow);
            if (espera != null) {
                deferred.add(new Deferred(c, espera));
                continue;
            }
            lines.add(new Line(c.kind(), c.bookingId(), c.paymentId(), null, c.classStart(), c.studentLabel(),
                    c.grossCop(), c.commissionRateBps(), c.commissionCop(), c.netCop(), describe(c)));
        }
        for (PendingAdjustment a : adjustments) {
            lines.add(new Line(a.kind(), a.bookingId(), null, a.adjustmentId(), null, null, 0, null, 0,
                    a.amountCop(), a.description()));
        }
        long gross = 0;
        long commission = 0;
        long adjustmentsCop = 0;
        for (Line l : lines) {
            if (l.kind() == PayoutLineKind.CLASS || l.kind() == PayoutLineKind.LATE_CANCELLATION) {
                gross += l.grossCop();
                commission += l.commissionCop();
            } else {
                adjustmentsCop += l.netCop();
            }
        }
        return new Result(lines, deferred, gross, commission, adjustmentsCop, gross - commission + adjustmentsCop);
    }

    /**
     * Por qué una clase no entra todavía, en palabras para el profe, o {@code null} si ya entra en el
     * corte dado: «Tiene un reclamo abierto» o «En plazo de reclamo hasta el 18 de octubre».
     */
    public static String pendingReason(Candidate c, Instant cutoff, Duration claimWindow) {
        if (c.openDispute()) {
            return "Tiene un reclamo abierto";
        }
        Instant venceElReclamo = c.classEnd().plus(claimWindow);
        if (venceElReclamo.isAfter(cutoff)) {
            return "En plazo de reclamo hasta el " + diaYMes(venceElReclamo);
        }
        return null;
    }

    /**
     * Lo que ve el profe en «Clases por liquidar», mirado desde {@code ahora}: el reclamo abierto, el
     * plazo de reclamo si no alcanza a vencer antes del próximo corte, o el corte en el que entra.
     */
    public static String whenItEnters(Candidate c, Instant ahora, Duration claimWindow) {
        if (c.openDispute()) {
            return "Tiene un reclamo abierto";
        }
        Fortnight proxima = next(ahora);
        Instant venceElReclamo = c.classEnd().plus(claimWindow);
        if (venceElReclamo.isAfter(proxima.cutoff())) {
            return "En plazo de reclamo hasta el " + diaYMes(venceElReclamo);
        }
        return "Entra en el corte del " + diaYMes(proxima.cutoff());
    }

    /**
     * «Ana R.»: el nombre y la inicial del apellido, que es lo que la liquidación dice del estudiante.
     * Con tres palabras el apellido es la última («Ana María Ramírez»); con cuatro o más, la penúltima
     * («Juan Carlos Pérez Díaz»): en Colombia el primer apellido va antes del segundo.
     */
    public static String studentLabel(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Estudiante";
        }
        String[] partes = fullName.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        String apellido = partes.length >= 4 ? partes[partes.length - 2] : partes[partes.length - 1];
        return partes[0] + " " + apellido.charAt(0) + ".";
    }

    private static String describe(Candidate c) {
        return c.kind() == PayoutLineKind.LATE_CANCELLATION
                ? "Cancelación tardía del estudiante"
                : "Clase del " + FechasEnPalabras.diaCorto(c.classStart());
    }

    private static String diaYMes(Instant instante) {
        ZonedDateTime z = instante.atZone(BusinessZone.BOGOTA);
        return z.getDayOfMonth() + " de " + z.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                java.util.Locale.forLanguageTag("es-CO"));
    }
}
