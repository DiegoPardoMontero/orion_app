package co.orion.scheduling.domain;

import java.time.ZonedDateTime;

/** Cupo reservable de 60 minutos: [startsAt, endsAt). */
public record Slot(ZonedDateTime startsAt, ZonedDateTime endsAt) {
}
