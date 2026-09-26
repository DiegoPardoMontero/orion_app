package co.orion.billing.application;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.time.BusinessZone;

/**
 * Los reportes contables del mandato (brief de liquidaciones, paso 6). Todo sale de las mismas tablas
 * que las liquidaciones, así que cuadran con ellas: lo recibido en nombre de cada profe (sus pagos
 * aprobados y no devueltos), la comisión, lo entregado (las liquidaciones pagadas) y lo pendiente.
 *
 * <p>Las fechas se cuentan en hora de Bogotá: un pago del 31 de diciembre a las 8 de la noche es de
 * ese año, aunque en UTC ya sea enero.
 */
@Service
public class PayoutReports {

    /** Un pago «recibido en nombre del profe»: aprobado y no devuelto ni anulado. */
    private static final String RECIBIDO = "p.paid_at is not null and p.status in ('PAID', 'RELEASED', 'DISPUTED')";

    /** La referencia de 3.500 UVT que el panel pone junto al recaudo del año. */
    public static final int REFERENCIA_UVT = 3500;

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final PayoutViews views;

    public PayoutReports(JdbcTemplate jdbc, PlatformSettingsService settings, PayoutViews views) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.views = views;
    }

    /** Libro de mandato: una fila por reserva con pago aprobado entre esas fechas (incluidas). */
    public List<LedgerRow> ledger(LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                select p.paid_at, p.provider_reference, p.status, s.full_name as student,
                       d.document_type, d.document_number, pr.full_name as professor,
                       p.amount_cop, p.commission_cop, p.professor_earnings_cop,
                       o.period_start, o.period_end, o.status as payout_status, o.paid_on
                  from payments p
                  join users s on s.id = p.student_id
                  join users pr on pr.id = p.professor_id
                  left join professor_payout_details d on d.professor_id = p.professor_id
                  left join payout_lines l on l.booking_id = p.booking_id and l.kind in ('CLASS', 'LATE_CANCELLATION')
                  left join payouts o on o.id = l.payout_id
                 where p.paid_at is not null and p.paid_at >= ? and p.paid_at < ?
                 order by p.paid_at
                """, (rs, i) -> new LedgerRow(
                        rs.getTimestamp("paid_at").toInstant(), rs.getString("provider_reference"), rs.getString("status"),
                        rs.getString("student"),
                        rs.getString("document_type") != null ? rs.getString("document_type") + " " + rs.getString("document_number") : null,
                        rs.getString("professor"), rs.getLong("amount_cop"), rs.getLong("commission_cop"),
                        rs.getLong("professor_earnings_cop"),
                        rs.getDate("period_start") != null ? rs.getDate("period_start").toLocalDate() : null,
                        rs.getDate("period_end") != null ? rs.getDate("period_end").toLocalDate() : null,
                        rs.getString("payout_status"),
                        rs.getDate("paid_on") != null ? rs.getDate("paid_on").toLocalDate() : null),
                inicio(desde), inicio(hasta.plusDays(1)));
    }

    /**
     * Resumen anual por profe: lo recibido en su nombre, la comisión, lo entregado en el año y lo que
     * al 31 de diciembre todavía no se le había pagado. Es la base del certificado y de la exógena.
     */
    public List<AnnualRow> annual(int anio) {
        LocalDate cierre = LocalDate.of(anio, 12, 31);
        return jdbc.query("""
                with profes as (
                    select distinct p.professor_id from payments p
                     where %1$s and p.paid_at >= ? and p.paid_at < ?
                    union
                    select o.professor_id from payouts o where o.status = 'PAID' and extract(year from o.paid_on) = ?
                )
                select pr.id, pr.full_name, d.document_type, d.document_number,
                       (select coalesce(sum(p.amount_cop), 0) from payments p where p.professor_id = pr.id
                          and %1$s and p.paid_at >= ? and p.paid_at < ?) as recibido,
                       (select coalesce(sum(p.commission_cop), 0) from payments p where p.professor_id = pr.id
                          and %1$s and p.paid_at >= ? and p.paid_at < ?) as comision,
                       (select coalesce(sum(o.amount_cop), 0) from payouts o where o.professor_id = pr.id
                          and o.status = 'PAID' and extract(year from o.paid_on) = ?) as entregado,
                       (select coalesce(sum(p.professor_earnings_cop), 0) from payments p where p.professor_id = pr.id
                          and %1$s and p.paid_at >= ? and p.paid_at < ?
                          and not exists (select 1 from payout_lines l join payouts o on o.id = l.payout_id
                                           where l.booking_id = p.booking_id and o.status = 'PAID' and o.paid_on <= ?)) as pendiente
                  from profes x
                  join users pr on pr.id = x.professor_id
                  left join professor_payout_details d on d.professor_id = pr.id
                 order by pr.full_name
                """.formatted(RECIBIDO), (rs, i) -> new AnnualRow(
                        rs.getObject("id", UUID.class), rs.getString("full_name"),
                        rs.getString("document_type") != null ? rs.getString("document_type") + " " + rs.getString("document_number") : null,
                        rs.getLong("recibido"), rs.getLong("comision"), rs.getLong("entregado"), rs.getLong("pendiente"), 0),
                inicioDelAnio(anio), inicioDelAnio(anio + 1), anio,
                inicioDelAnio(anio), inicioDelAnio(anio + 1),
                inicioDelAnio(anio), inicioDelAnio(anio + 1),
                anio,
                inicioDelAnio(anio), inicioDelAnio(anio + 1), Date.valueOf(cierre));
    }

    /** Comisiones por mes: el ingreso propio de Orión. */
    public List<MonthRow> commissionsByMonth(int anio) {
        return jdbc.query("""
                select extract(month from p.paid_at at time zone 'America/Bogota')::int as mes,
                       count(*) as clases, coalesce(sum(p.amount_cop), 0) as recibido,
                       coalesce(sum(p.commission_cop), 0) as comision
                  from payments p
                 where %s and p.paid_at >= ? and p.paid_at < ?
                 group by 1 order by 1
                """.formatted(RECIBIDO), (rs, i) -> new MonthRow(rs.getInt("mes"), rs.getInt("clases"),
                        rs.getLong("recibido"), rs.getLong("comision")),
                inicioDelAnio(anio), inicioDelAnio(anio + 1));
    }

    /**
     * El indicador del panel: comisiones y recaudo del año. El recaudo es lo que entró por la
     * pasarela (sin el saldo a favor, que ya estaba en la cuenta), y se expresa también en UVT.
     */
    public YearSummary yearSummary(int anio) {
        Long comision = jdbc.queryForObject("select coalesce(sum(p.commission_cop), 0) from payments p where "
                + RECIBIDO + " and p.paid_at >= ? and p.paid_at < ?", Long.class, inicioDelAnio(anio), inicioDelAnio(anio + 1));
        Long recaudo = jdbc.queryForObject("""
                select coalesce(sum(p.charged_cop), 0) from payments p
                 where p.paid_at is not null and p.status <> 'CANCELLED' and p.paid_at >= ? and p.paid_at < ?
                """, Long.class, inicioDelAnio(anio), inicioDelAnio(anio + 1));
        int uvt = settings.getInt("uvt_cop");
        long recaudoCop = recaudo == null ? 0 : recaudo;
        return new YearSummary(anio, comision == null ? 0 : comision, recaudoCop, uvt,
                Math.round(recaudoCop * 10.0 / uvt) / 10.0, REFERENCIA_UVT,
                "El recaudo pasa por tu cuenta bancaria: súmalo a tus otras consignaciones del año.");
    }

    /** El borrador del certificado de un profe en un año, para que lo firme un contador público. */
    public CertificateDraft certificateDraft(UUID professorId, int anio) {
        AnnualRow fila = annual(anio).stream().filter(r -> r.professorId().equals(professorId)).findFirst()
                .orElse(null);
        String nombre = fila != null ? fila.professorName()
                : jdbc.queryForObject("select full_name from users where id = ?", String.class, professorId);
        return new CertificateDraft(anio, views.mandatario(), views.documentoDelMandatario(), nombre,
                fila != null ? fila.professorDocument() : null, fila != null ? fila.receivedCop() : 0,
                fila != null ? fila.commissionCop() : 0, fila != null ? fila.deliveredCop() : 0, 0);
    }

    private static Timestamp inicio(LocalDate dia) {
        return Timestamp.from(dia.atStartOfDay(BusinessZone.BOGOTA).toInstant());
    }

    private static Timestamp inicioDelAnio(int anio) {
        return inicio(LocalDate.of(anio, 1, 1));
    }

    public record LedgerRow(Instant paidAt, String providerReference, String paymentStatus, String studentName,
                            String professorDocument, String professorName, long grossCop, long commissionCop,
                            long netCop, LocalDate payoutPeriodStart, LocalDate payoutPeriodEnd, String payoutStatus,
                            LocalDate payoutPaidOn) {
    }

    public record AnnualRow(UUID professorId, String professorName, String professorDocument, long receivedCop,
                            long commissionCop, long deliveredCop, long pendingCop, long withheldCop) {
    }

    public record MonthRow(int month, int classes, long receivedCop, long commissionCop) {
    }

    public record YearSummary(int year, long commissionCop, long collectedCop, int uvtCop, double collectedUvt,
                              int referenceUvt, String note) {
    }

    public record CertificateDraft(int year, String mandataryName, String mandataryDocument, String professorName,
                                   String professorDocument, long receivedCop, long commissionCop, long deliveredCop,
                                   long withheldCop) {
    }
}
