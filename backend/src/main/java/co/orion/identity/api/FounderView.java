package co.orion.identity.api;

import java.time.Instant;
import java.time.ZonedDateTime;

import co.orion.catalog.domain.FounderTerms;
import co.orion.shared.time.BusinessZone;

/**
 * El beneficio de profe fundador, como lo ve el propio profe y el admin.
 *
 * @param status {@code NOT_STARTED}, {@code ACTIVE} o {@code ENDED}
 */
public record FounderView(int rateBps, int periodMonths, ZonedDateTime startedAt, ZonedDateTime until,
                          String status) {

    /** {@code null} si el profe no es fundador. */
    public static FounderView of(FounderTerms terms, Instant now) {
        if (terms == null) {
            return null;
        }
        return new FounderView(terms.rateBps(), terms.periodMonths(),
                terms.startedAt() == null ? null : terms.startedAt().atZone(BusinessZone.BOGOTA),
                terms.until() == null ? null : terms.until().atZone(BusinessZone.BOGOTA),
                terms.status(now).name());
    }
}
