package co.orion.identity.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * El admin habilitó a un profe para clases con adolescentes (Pardo, 15/10/2026: «comunícale por
 * correo y por mensaje de Orión que ya está habilitado»). Correo y notificación salen AFTER_COMMIT.
 */
public record MinorsClearanceGrantedEvent(UUID professorId, String email, String firstName, LocalDate until) {
}
