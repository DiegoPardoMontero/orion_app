package co.orion.messaging.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.messaging.domain.RigelKind;

/**
 * Los mensajes de Rigel (V66). En SQL y no en JPA: son inserciones que la base puede rechazar sin
 * error —«una vez cada uno» es un índice único— y lecturas por persona, nada más.
 */
@Repository
public class RigelMessages {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {
    };

    public record Guardado(UUID id, RigelKind kind, Map<String, String> params, Instant createdAt, Instant readAt) {
    }

    private final JdbcTemplate jdbc;

    public RigelMessages(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return si se guardó; falso si esa persona ya lo tenía (los que salen una sola vez). */
    public boolean guardar(UUID userId, RigelKind kind, Map<String, String> params, Instant cuando) {
        return jdbc.update("""
                insert into rigel_messages (user_id, kind, params, created_at)
                values (?, ?, cast(? as jsonb), ?)
                on conflict do nothing
                """, userId, kind.name(), json(params), Timestamp.from(cuando)) > 0;
    }

    public boolean tiene(UUID userId, RigelKind kind) {
        Boolean hay = jdbc.queryForObject(
                "select exists (select 1 from rigel_messages where user_id = ? and kind = ?)",
                Boolean.class, userId, kind.name());
        return Boolean.TRUE.equals(hay);
    }

    public List<Guardado> de(UUID userId) {
        return jdbc.query("""
                select id, kind, params::text, created_at, read_at from rigel_messages
                 where user_id = ? order by created_at, id
                """, (rs, i) -> new Guardado(
                rs.getObject(1, UUID.class),
                RigelKind.valueOf(rs.getString(2)),
                mapa(rs.getString(3)),
                rs.getTimestamp(4).toInstant(),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()), userId);
    }

    public int noLeidos(UUID userId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from rigel_messages where user_id = ? and read_at is null", Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void marcarLeidos(UUID userId, Instant cuando) {
        jdbc.update("update rigel_messages set read_at = ? where user_id = ? and read_at is null",
                Timestamp.from(cuando), userId);
    }

    private static String json(Map<String, String> params) {
        try {
            return JSON.writeValueAsString(params == null ? Map.of() : params);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Datos del mensaje de Rigel ilegibles", ex);
        }
    }

    private static Map<String, String> mapa(String json) {
        try {
            return json == null ? Map.of() : JSON.readValue(json, MAPA);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
