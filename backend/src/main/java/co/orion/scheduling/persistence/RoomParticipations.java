package co.orion.scheduling.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Quién entró a cada sala, si sigue dentro y cuánto habló (V46), escrito desde los webhooks de JaaS.
 *
 * <p>En SQL y no con JPA porque cada evento es un upsert con condiciones que la base resuelve de
 * una vez: la primera entrada no se pisa, «dentro» solo cambia si el evento es más reciente que el
 * último visto (los webhooks llegan desordenados), y el tiempo hablado se suma sesión a sesión.
 */
@Repository
public class RoomParticipations {

    private final JdbcTemplate jdbc;

    public RoomParticipations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra la llave del evento. Falso si ya estaba: ese evento ya se procesó. */
    public boolean primeraVez(String idempotencyKey, String eventType) {
        return jdbc.update("""
                insert into video_webhook_events (idempotency_key, event_type) values (?, ?)
                on conflict (idempotency_key) do nothing
                """, idempotencyKey, eventType) == 1;
    }

    public void entro(UUID bookingId, UUID userId, Instant cuando) {
        jdbc.update("""
                insert into room_participations (booking_id, user_id, first_joined_at, last_event_at, inside)
                values (?, ?, ?, ?, true)
                on conflict (booking_id, user_id) do update set
                    first_joined_at = coalesce(room_participations.first_joined_at, excluded.first_joined_at),
                    inside = case when excluded.last_event_at >= room_participations.last_event_at
                                  then true else room_participations.inside end,
                    last_event_at = greatest(room_participations.last_event_at, excluded.last_event_at)
                """, bookingId, userId, Timestamp.from(cuando), Timestamp.from(cuando));
    }

    public void salio(UUID bookingId, UUID userId, Instant cuando) {
        jdbc.update("""
                insert into room_participations (booking_id, user_id, last_event_at, inside)
                values (?, ?, ?, false)
                on conflict (booking_id, user_id) do update set
                    inside = case when excluded.last_event_at >= room_participations.last_event_at
                                  then false else room_participations.inside end,
                    last_event_at = greatest(room_participations.last_event_at, excluded.last_event_at)
                """, bookingId, userId, Timestamp.from(cuando));
    }

    /** Una sesión de sala manda su total al cerrarse; si hubo dos sesiones, se suman. */
    public void hablo(UUID bookingId, UUID userId, long ms, Instant cuando) {
        jdbc.update("""
                insert into room_participations (booking_id, user_id, last_event_at, speaking_ms)
                values (?, ?, ?, ?)
                on conflict (booking_id, user_id) do update set
                    speaking_ms = coalesce(room_participations.speaking_ms, 0) + excluded.speaking_ms
                """, bookingId, userId, Timestamp.from(cuando), (int) Math.min(ms, Integer.MAX_VALUE));
    }

    public boolean estaDentro(UUID bookingId, UUID userId) {
        List<Boolean> r = jdbc.queryForList(
                "select inside from room_participations where booking_id = ? and user_id = ?",
                Boolean.class, bookingId, userId);
        return !r.isEmpty() && Boolean.TRUE.equals(r.getFirst());
    }

    /** Lo que se sabe de una clase: la fila de cada participante. */
    public record Participacion(UUID userId, Instant primeraEntrada, Integer hablaMs) {
    }

    public List<Participacion> de(UUID bookingId) {
        return jdbc.query("""
                select user_id, first_joined_at, speaking_ms from room_participations
                where booking_id = ?
                """, (rs, i) -> new Participacion(
                        rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("first_joined_at") == null ? null
                                : rs.getTimestamp("first_joined_at").toInstant(),
                        (Integer) rs.getObject("speaking_ms")), bookingId);
    }

    /** Una clase medida: cuánto habló el estudiante y cuánto el profesor. */
    public record ClaseMedida(UUID bookingId, Instant empezo, Integer estudianteMs, Integer profesorMs) {
    }

    /** Las últimas clases entre un profesor y un estudiante que traen tiempo hablado. */
    public List<ClaseMedida> ultimasClases(UUID profesorId, UUID estudianteId, Instant antesDe, int cuantas) {
        return jdbc.query("""
                select b.id, b.starts_at, st.speaking_ms as est, pr.speaking_ms as prof
                from bookings b
                left join room_participations st on st.booking_id = b.id and st.user_id = b.student_id
                left join room_participations pr on pr.booking_id = b.id and pr.user_id = b.professor_id
                where b.professor_id = ? and b.student_id = ? and b.starts_at < ?
                  and (st.speaking_ms is not null or pr.speaking_ms is not null)
                order by b.starts_at desc
                limit ?
                """, (rs, i) -> new ClaseMedida(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("starts_at").toInstant(),
                        (Integer) rs.getObject("est"),
                        (Integer) rs.getObject("prof")),
                profesorId, estudianteId, Timestamp.from(antesDe), cuantas);
    }

    /**
     * Por profesor, en la ventana dada: clases con datos del aula, qué parte de la palabra tuvo el
     * estudiante y cuánto tardó el profesor en entrar. Informativo: no alimenta sanciones.
     */
    public record AulaDelProfesor(UUID profesorId, String nombre, int clases, Double parteDelEstudiante,
                                  Double minutosDeRetrasoPromedio, int llegadasTarde) {
    }

    public List<AulaDelProfesor> porProfesor(Instant desde, Instant hasta) {
        return jdbc.query("""
                select u.id, u.full_name, count(*) as clases,
                       avg(case when st.speaking_ms + pr.speaking_ms > 0
                                then st.speaking_ms::numeric / (st.speaking_ms + pr.speaking_ms) end) as parte,
                       avg(greatest(0, extract(epoch from (pr.first_joined_at - b.starts_at)) / 60.0))
                           filter (where pr.first_joined_at is not null) as retraso,
                       count(*) filter (where pr.first_joined_at > b.starts_at + interval '5 minutes') as tarde
                from bookings b
                join users u on u.id = b.professor_id
                left join room_participations pr on pr.booking_id = b.id and pr.user_id = b.professor_id
                left join room_participations st on st.booking_id = b.id and st.user_id = b.student_id
                where b.starts_at >= ? and b.starts_at < ?
                  and (pr.booking_id is not null or st.booking_id is not null)
                group by u.id, u.full_name
                order by u.full_name
                """, (rs, i) -> new AulaDelProfesor(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getInt("clases"),
                        rs.getObject("parte") == null ? null : rs.getDouble("parte"),
                        rs.getObject("retraso") == null ? null : rs.getDouble("retraso"),
                        rs.getInt("tarde")),
                Timestamp.from(desde), Timestamp.from(hasta));
    }
}
