package co.orion.catalog.domain;

import java.time.Instant;

/**
 * La promesa del profe fundador, tal como se le hizo (brief del profe fundador, 25/09/2026).
 *
 * <p>El porcentaje y la duración se copian al otorgar el beneficio y no se vuelven a leer de los
 * ajustes: cambiar {@code founder_commission_rate_bps} después no puede tocar lo que ya se prometió.
 *
 * @param rateBps      la comisión de fundador, en puntos básicos (1500 son 15 %)
 * @param periodMonths cuántos meses calendario dura, desde la primera clase pagada
 * @param startedAt    cuándo empezó a contar; {@code null} mientras no haya una clase pagada
 * @param until        cuándo termina (a las 00:00 de Bogotá); {@code null} mientras no empiece
 */
public record FounderTerms(int rateBps, int periodMonths, Instant startedAt, Instant until) {

    public enum Status {
        /** Otorgado, sin clases pagadas todavía: el beneficio ya aplica. */
        NOT_STARTED,
        /** Contando: aplica a las reservas creadas antes de {@code until}. */
        ACTIVE,
        /** Terminó: las reservas nuevas llevan la comisión base. */
        ENDED
    }

    public FounderTerms {
        if (rateBps < 0 || rateBps > 10000) {
            throw new IllegalArgumentException("La comisión de fundador debe estar entre 0 y 10000 bps");
        }
        if (periodMonths <= 0) {
            throw new IllegalArgumentException("La duración del beneficio debe ser positiva");
        }
        if ((startedAt == null) != (until == null)) {
            throw new IllegalArgumentException("El inicio y el fin del beneficio van juntos");
        }
    }

    public Status status(Instant now) {
        if (startedAt == null) {
            return Status.NOT_STARTED;
        }
        return now.isBefore(until) ? Status.ACTIVE : Status.ENDED;
    }
}
