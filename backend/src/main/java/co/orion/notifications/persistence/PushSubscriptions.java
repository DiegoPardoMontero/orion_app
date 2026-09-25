package co.orion.notifications.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Los navegadores suscritos a avisos (V61). En SQL: son upserts y borrados por endpoint, nada más. */
@Repository
public class PushSubscriptions {

    public record Suscripcion(UUID id, UUID userId, String endpoint, String p256dh, String auth) {
    }

    private final JdbcTemplate jdbc;

    public PushSubscriptions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cuántos navegadores guarda una persona. Celular, portátil y alguno más; no cientos. */
    public static final int MAXIMO_POR_PERSONA = 10;

    /**
     * Guarda la suscripción de este navegador. Si el endpoint ya existía —otra cuenta en el mismo
     * navegador— pasa a la cuenta actual: un navegador avisa a quien está usándolo, no a quien lo usó.
     *
     * <p>Pasadas las {@link #MAXIMO_POR_PERSONA}, se olvida la más vieja. Sin tope, una cuenta podía
     * registrar miles de direcciones válidas pero inventadas —el servicio no las da por muertas con
     * un 404/410, así que nunca se limpiaban— y cada aviso suyo salía hacia todas, una a una.
     */
    public void guardar(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        jdbc.update("""
                insert into push_subscriptions (user_id, endpoint, p256dh, auth, user_agent)
                values (?, ?, ?, ?, ?)
                on conflict (endpoint) do update set
                    user_id = excluded.user_id, p256dh = excluded.p256dh, auth = excluded.auth,
                    user_agent = excluded.user_agent, created_at = now()
                """, userId, endpoint, p256dh, auth, userAgent);
        jdbc.update("""
                delete from push_subscriptions where user_id = ? and id not in (
                    select id from push_subscriptions where user_id = ?
                     order by created_at desc, id limit ?)
                """, userId, userId, MAXIMO_POR_PERSONA);
    }

    /** Solo la propia: nadie puede apagar los avisos de otra persona conociendo su endpoint. */
    public int borrar(UUID userId, String endpoint) {
        return jdbc.update("delete from push_subscriptions where user_id = ? and endpoint = ?", userId, endpoint);
    }

    public void borrarMuerta(UUID id) {
        jdbc.update("delete from push_subscriptions where id = ?", id);
    }

    public List<Suscripcion> de(UUID userId) {
        return jdbc.query("select id, user_id, endpoint, p256dh, auth from push_subscriptions where user_id = ?",
                (rs, i) -> new Suscripcion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5)), userId);
    }

    public int cuantas(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?", Integer.class,
                userId);
        return n == null ? 0 : n;
    }

    public void usada(UUID id, Instant cuando) {
        jdbc.update("update push_subscriptions set last_sent_at = ? where id = ?", Timestamp.from(cuando), id);
    }
}
