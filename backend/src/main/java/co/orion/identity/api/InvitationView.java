package co.orion.identity.api;

import java.time.ZonedDateTime;

/**
 * Lo que ve quien abre el enlace de una invitación, antes de tener cuenta.
 *
 * <p>Vencida o usada, solo viaja el estado: ni el correo ni el nombre de quien invita, para no
 * contarle nada a quien encuentre un enlace viejo. Un token que no existe se muestra como vencido,
 * así no se revela si existió.
 *
 * @param state {@code VALID}, {@code EXPIRED} o {@code USED}
 */
public record InvitationView(String state,
                             String email,
                             String professorName,
                             String invitedByName,
                             String invitedByTitle,
                             ZonedDateTime expiresAt,
                             FounderOffer founder) {

    /** El beneficio que trae la invitación, con los números vigentes: la pantalla no los inventa. */
    public record FounderOffer(int rateBps, int periodMonths, int baseRateBps) {
    }

    public static InvitationView soloEstado(String state) {
        return new InvitationView(state, null, null, null, null, null, null);
    }
}
