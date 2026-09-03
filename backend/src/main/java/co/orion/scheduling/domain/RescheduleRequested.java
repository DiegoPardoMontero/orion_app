package co.orion.scheduling.domain;

import java.util.UUID;

/** Alguien propuso mover una clase. Lo escucha notifications para avisarle a la contraparte. */
public record RescheduleRequested(UUID requestId) {
}
