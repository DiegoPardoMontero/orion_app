package co.orion.reputation.domain;

import java.util.UUID;

/**
 * Una sanción empezó a regir (confirmada o aplicada a mano) o se levantó. Las propuestas no: hasta
 * que una persona las confirma no le pasan nada al profesor.
 */
public record SanctionChangedEvent(UUID sanctionId, UUID professorId, SanctionType type, String reason,
                                   boolean lifted) {

    /** Lo que le pasa, dicho sin eufemismos y sin tono de castigo. Lo usan la campana y el correo. */
    public String enPalabras() {
        if (lifted) {
            return "Se levantó una medida sobre tu cuenta";
        }
        return switch (type) {
            case WARNING -> "Orión te dejó un aviso";
            case VISIBILITY_REDUCED -> "Tu perfil saldrá más abajo en el buscador por dos semanas";
            case BOOKINGS_SUSPENDED -> "No recibirás reservas nuevas durante una semana";
            case PROFILE_HIDDEN -> "Tu perfil salió del buscador mientras revisamos tu caso";
            case ACCOUNT_SUSPENDED -> "Tu cuenta quedó suspendida";
        };
    }
}
