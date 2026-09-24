package co.orion.support.domain;

import java.util.UUID;

/** Orión respondió una solicitud de soporte. Quien la abrió tiene que enterarse sin entrar a mirar. */
public record SupportAnsweredEvent(UUID ticketId, String code, UUID userId, String subject) {
}
