package co.orion.scheduling.api;

import java.time.ZonedDateTime;

/**
 * El aula, vista por quien va a entrar: en qué punto está el reloj, con quién es la clase, y —solo
 * si ya se puede— la llave para entrar.
 *
 * <p><strong>Por qué no es solo el token.</strong> La antesala tiene que dibujarse antes de que
 * haya nada que abrir: con quién vas a hablar, cuánto falta, y la prueba de micrófono. Si el
 * servidor respondiera con un error mientras la sala está cerrada, la pantalla más importante —la
 * de quien llegó diez minutos antes porque está nervioso— no tendría datos que mostrar.
 *
 * <p>Por eso {@code token}, {@code domain} y {@code room} son nulos salvo en {@code OPEN} y
 * {@code STARTED}. Una llave solo existe cuando sirve.
 */
public record ClassroomResponse(
        /** CLOSED (aún no), OPEN (ya se puede entrar), STARTED (la clase corre), ENDED (se acabó). */
        String state,
        ZonedDateTime startsAt,
        ZonedDateTime endsAt,
        /** Desde cuándo se puede entrar: diez minutos antes de la hora. */
        ZonedDateTime opensAt,
        /** Cuándo deja de servir el acceso: quince minutos después del final. */
        ZonedDateTime expiresAt,
        int classMinutes,
        /** Si esta persona modera. El profesor sí; el estudiante no. */
        boolean moderator,
        /** Con quién es la clase. */
        Counterpart counterpart,
        /** Si la otra persona ya está dentro. Lo sabe el servidor por los webhooks de JaaS. */
        boolean counterpartPresent,
        /** Nombre con el que entrará, para que el aula no tenga que adivinarlo. */
        String displayName,
        String domain,
        String room,
        String token) {

    public record Counterpart(String name, String firstName, String photoUrl, String headline) {
    }
}
