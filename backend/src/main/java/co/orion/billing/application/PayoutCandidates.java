package co.orion.billing.application;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import co.orion.billing.domain.PayoutCalculator.Candidate;
import co.orion.billing.domain.PayoutCalculator;
import co.orion.billing.domain.PayoutLineKind;

/**
 * Lo que el corte puede liquidar: cada pago liberado al profe que todavía no está en una liquidación,
 * con su clase, el estudiante y si tiene un reclamo abierto. Es una lectura de varias tablas (pagos,
 * reservas, usuarios, reclamos) y va en SQL a propósito: el calculador decide, esto solo junta.
 *
 * <p>Entran las clases cerradas como dictadas ({@code COMPLETED}, {@code NO_SHOW_STUDENT}) y las
 * cancelaciones tardías del estudiante, cuyo dinero la política de cancelación ya le dio al profe
 * (decisión de Pardo del 25/09/2026).
 */
@Component
public class PayoutCandidates {

    private static final String SQL = """
            select p.id as payment_id, p.booking_id, p.professor_id, b.status as booking_status,
                   b.starts_at, b.ends_at, p.amount_cop, p.commission_rate_bps, p.commission_cop,
                   p.professor_earnings_cop, u.full_name as student_name,
                   exists (select 1 from disputes d where d.booking_id = b.id
                           and d.status in ('OPEN', 'UNDER_REVIEW')) as open_dispute
              from payments p
              join bookings b on b.id = p.booking_id
              join users u on u.id = p.student_id
             where p.status = 'RELEASED'
               and b.status in ('COMPLETED', 'NO_SHOW_STUDENT', 'CANCELLED_BY_STUDENT')
               and not exists (select 1 from payout_lines l where l.booking_id = p.booking_id
                               and l.kind in ('CLASS', 'LATE_CANCELLATION'))
            """;

    private final JdbcTemplate jdbc;

    public PayoutCandidates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Todas, agrupadas por profe. */
    public Map<UUID, List<Candidate>> byProfessor() {
        Map<UUID, List<Candidate>> porProfe = new LinkedHashMap<>();
        jdbc.query(SQL + " order by b.starts_at", rs -> {
            UUID profe = rs.getObject("professor_id", UUID.class);
            porProfe.computeIfAbsent(profe, k -> new ArrayList<>()).add(candidate(rs));
        });
        return porProfe;
    }

    public List<Candidate> ofProfessor(UUID professorId) {
        return jdbc.query(SQL + " and p.professor_id = ? order by b.starts_at", (rs, i) -> candidate(rs), professorId);
    }

    /**
     * Lo que el profe tiene «por liquidar» (brief de liquidaciones, paso 5): clases que ya terminaron
     * y todavía no están en una liquidación, cerradas o no. Las que siguen abiertas (el profe no ha
     * registrado asistencia) o con un reclamo abierto también, con su motivo: lo que el profe quiere
     * saber es cuándo le llega cada una.
     */
    public List<Candidate> pendingOf(UUID professorId, java.time.Instant ahora) {
        return jdbc.query("""
                select p.id as payment_id, p.booking_id, p.professor_id, b.status as booking_status,
                       b.starts_at, b.ends_at, p.amount_cop, p.commission_rate_bps, p.commission_cop,
                       p.professor_earnings_cop, u.full_name as student_name,
                       (p.status = 'DISPUTED' or exists (select 1 from disputes d where d.booking_id = b.id
                               and d.status in ('OPEN', 'UNDER_REVIEW'))) as open_dispute
                  from payments p
                  join bookings b on b.id = p.booking_id
                  join users u on u.id = p.student_id
                 where p.professor_id = ?
                   and p.status in ('PAID', 'RELEASED', 'DISPUTED')
                   and b.ends_at <= ?
                   and b.status in ('CONFIRMED', 'UNDER_REVIEW', 'COMPLETED', 'NO_SHOW_STUDENT', 'CANCELLED_BY_STUDENT')
                   and p.professor_earnings_cop > 0
                   and not exists (select 1 from payout_lines l where l.booking_id = p.booking_id
                                   and l.kind in ('CLASS', 'LATE_CANCELLATION'))
                 order by b.starts_at
                """, (rs, i) -> candidate(rs), professorId, Timestamp.from(ahora));
    }

    private static Candidate candidate(java.sql.ResultSet rs) throws java.sql.SQLException {
        PayoutLineKind kind = "CANCELLED_BY_STUDENT".equals(rs.getString("booking_status"))
                ? PayoutLineKind.LATE_CANCELLATION : PayoutLineKind.CLASS;
        return new Candidate(
                rs.getObject("booking_id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                kind,
                rs.getObject("starts_at", Timestamp.class).toInstant(),
                rs.getObject("ends_at", Timestamp.class).toInstant(),
                PayoutCalculator.studentLabel(rs.getString("student_name")),
                rs.getLong("amount_cop"),
                rs.getInt("commission_rate_bps"),
                rs.getLong("commission_cop"),
                rs.getLong("professor_earnings_cop"),
                rs.getBoolean("open_dispute"));
    }
}
