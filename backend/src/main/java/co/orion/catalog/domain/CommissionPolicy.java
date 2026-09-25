package co.orion.catalog.domain;

import java.time.Instant;
import java.time.LocalDate;

import co.orion.shared.time.BusinessZone;

/**
 * Qué comisión lleva una reserva (brief del profe fundador, 25/09/2026).
 *
 * <p>La comisión de Orión es la base de {@code platform_settings}. El profe fundador paga la suya
 * mientras la reserva se cree antes de {@code until}, y también mientras el conteo no haya empezado
 * —el beneficio aplica desde que se otorga—. La comisión se congela al crear la reserva, así que una
 * reserva hecha el último día del beneficio conserva el 15 % aunque la clase se dicte después.
 *
 * <p>Clase pura, como {@link RateBreakdown} y {@code SlotCalculator}: sin Spring, sin repositorios
 * y sin reloj del sistema; el «ahora» entra por parámetro.
 */
public final class CommissionPolicy {

    private CommissionPolicy() {
    }

    /**
     * @param baseRateBps      la comisión de Orión, del ajuste {@code commission_rate_bps}
     * @param founder          la promesa del profe, o {@code null} si no es fundador
     * @param bookingCreatedAt cuándo se crea la reserva
     */
    public static int effectiveRate(int baseRateBps, FounderTerms founder, Instant bookingCreatedAt) {
        if (founder == null) {
            return baseRateBps;
        }
        if (founder.until() == null || bookingCreatedAt.isBefore(founder.until())) {
            return founder.rateBps();
        }
        return baseRateBps;
    }

    /**
     * Cuándo termina el beneficio: la fecha de inicio en Bogotá más los meses calendario, a las 00:00
     * de Bogotá. Un 31 de octubre más tres meses es el 31 de enero; un 30 de noviembre, el 28 (o 29)
     * de febrero, que es como cuenta {@link LocalDate#plusMonths} y también Postgres.
     */
    public static Instant founderUntil(Instant startedAt, int periodMonths) {
        LocalDate inicio = startedAt.atZone(BusinessZone.BOGOTA).toLocalDate();
        return inicio.plusMonths(periodMonths).atStartOfDay(BusinessZone.BOGOTA).toInstant();
    }
}
