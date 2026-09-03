package co.orion.admin.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.admin.api.PurgePreview;
import co.orion.identity.application.AdminAuditService;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * Borrado DEFINITIVO de clases y usuarios, para dejar el sistema limpio antes de abrirlo al público.
 *
 * <h2>Por qué SQL directo y no repositorios</h2>
 *
 * Un borrado en cascada tiene que ir en orden de dependencias y tocar tablas de cinco módulos. Con
 * repositorios habría que abrir una puerta de borrado en cada módulo —puertas que después quedan
 * abiertas para siempre y que nadie más debería usar—. Aquí el borrado vive en un solo sitio, se lee
 * de arriba abajo en el orden en que ocurre, y no contamina el dominio con operaciones que solo
 * existen para limpiar.
 *
 * <h2>Lo que NO hace</h2>
 *
 * No decide por ti. Si borrar una clase destruye un pago ya liquidado, te lo dice en la vista previa
 * y te deja seguir: es tu sistema y tu decisión. Lo que sí hace es no dejarte borrar a ciegas, y
 * dejar constancia en la auditoría de qué se borró, quién y por qué.
 */
@Service
public class PurgeService {

    private static final Logger log = LoggerFactory.getLogger(PurgeService.class);

    /** El texto que hay que escribir para confirmar. Corto, pero no accidental. */
    public static final String CONFIRMATION = "BORRAR";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AdminAuditService audit;
    private final Clock clock;

    public PurgeService(JdbcTemplate jdbc,
                        UserRepository users,
                        AdminAuditService audit,
                        Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ clases */

    @Transactional(readOnly = true)
    public PurgePreview previewBooking(UUID bookingId) {
        requireBookingExists(bookingId);

        List<PurgePreview.Row> rows = List.of(
                row("Reserva", 1),
                row("Pagos", count("select count(*) from payments where booking_id = ?", bookingId)),
                row("Aplicaciones de saldo",
                        count("""
                              select count(*) from payment_credit_applications a
                              join payments p on p.id = a.payment_id where p.booking_id = ?
                              """, bookingId)),
                row("Eventos de pasarela",
                        count("""
                              select count(*) from payment_events e
                              join payments p on p.id = e.payment_id where p.booking_id = ?
                              """, bookingId)),
                row("Saldos otorgados por esta clase",
                        count("select count(*) from student_credits where booking_id = ?", bookingId)),
                row("Reseñas", count("select count(*) from reviews where booking_id = ?", bookingId)),
                row("Registros de asistencia",
                        count("select count(*) from attendance_records where booking_id = ?", bookingId)),
                row("Reclamos", count("select count(*) from disputes where booking_id = ?", bookingId)),
                row("Propuestas de cambio",
                        count("select count(*) from reschedule_requests where booking_id = ?", bookingId)),
                row("Ausencias registradas",
                        count("select count(*) from professor_absences where booking_id = ?", bookingId)));

        PurgePreview.Money money = moneyOfBooking(bookingId);
        return new PurgePreview("booking", describeBooking(bookingId), rows, money,
                warningsFor(money, count("select count(*) from payout_items i join payments p "
                        + "on p.id = i.payment_id where p.booking_id = ?", bookingId)));
    }

    @Transactional
    public PurgePreview purgeBooking(UUID bookingId, User admin, String reason) {
        PurgePreview preview = previewBooking(bookingId);

        // De la hoja a la raíz: nada apunta a lo que se borra cuando le llega el turno.
        jdbc.update("delete from payout_items where payment_id in (select id from payments where booking_id = ?)", bookingId);
        jdbc.update("delete from payment_credit_applications where payment_id in (select id from payments where booking_id = ?)", bookingId);
        jdbc.update("delete from payment_events where payment_id in (select id from payments where booking_id = ?)", bookingId);
        jdbc.update("delete from student_credits where booking_id = ?", bookingId);
        jdbc.update("delete from payments where booking_id = ?", bookingId);
        jdbc.update("delete from professor_absences where booking_id = ?", bookingId);
        jdbc.update("delete from reschedule_requests where booking_id = ?", bookingId);
        jdbc.update("delete from disputes where booking_id = ?", bookingId);
        jdbc.update("delete from reviews where booking_id = ?", bookingId);
        jdbc.update("delete from attendance_records where booking_id = ?", bookingId);
        jdbc.update("delete from bookings where id = ?", bookingId);

        audit.record(admin.getId(), "PURGE_BOOKING", "BOOKING", bookingId,
                summaryOf(preview) + (reason != null ? " · " + reason : ""));
        log.warn("PURGA de la reserva {} por el admin {}", bookingId, admin.getEmail());
        return preview;
    }

    /* ----------------------------------------------------------------- usuarios */

    @Transactional(readOnly = true)
    public PurgePreview previewUser(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<PurgePreview.Row> rows = List.of(
                row("Usuario", 1),
                row("Clases como estudiante",
                        count("select count(*) from bookings where student_id = ?", userId)),
                row("Clases como profesor",
                        count("select count(*) from bookings where professor_id = ?", userId)),
                row("Pagos", count("select count(*) from payments where student_id = ? or professor_id = ?", userId, userId)),
                row("Saldos a favor", count("select count(*) from student_credits where student_id = ?", userId)),
                row("Liquidaciones", count("select count(*) from payouts where professor_id = ?", userId)),
                row("Reseñas escritas o recibidas",
                        count("select count(*) from reviews where student_id = ? or professor_id = ?", userId, userId)),
                row("Conversaciones",
                        count("select count(*) from conversations where student_id = ? or professor_id = ?", userId, userId)),
                row("Notificaciones", count("select count(*) from notifications where user_id = ?", userId)),
                row("Disponibilidad",
                        count("select count(*) from availability_rules where professor_id = ?", userId)
                        + count("select count(*) from availability_exceptions where professor_id = ?", userId)),
                row("Postulación y documentos",
                        count("select count(*) from teacher_applications where user_id = ?", userId)));

        PurgePreview.Money money = moneyOfUser(userId);
        List<String> warnings = new ArrayList<>(warningsFor(money,
                count("select count(*) from payout_items i join payments p on p.id = i.payment_id "
                      + "where p.student_id = ? or p.professor_id = ?", userId, userId)));

        if (user.getRole() == UserRole.ADMIN && users.countByRole(UserRole.ADMIN) <= 1) {
            warnings.add("Es el ÚNICO administrador: borrarlo te deja sin acceso al panel.");
        }
        return new PurgePreview("user", user.getFullName() + " · " + user.getEmail(),
                rows, money, warnings);
    }

    @Transactional
    public PurgePreview purgeUser(UUID userId, User admin, String reason) {
        if (userId.equals(admin.getId())) {
            throw new BusinessRuleViolationException("No puedes borrar tu propia cuenta");
        }
        User target = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (target.getRole() == UserRole.ADMIN && users.countByRole(UserRole.ADMIN) <= 1) {
            throw new BusinessRuleViolationException(
                    "Es el único administrador: borrarlo dejaría Orión sin acceso al panel");
        }

        PurgePreview preview = previewUser(userId);

        // Todas las clases de esta persona, en cualquiera de los dos papeles.
        List<UUID> bookingIds = jdbc.queryForList(
                "select id from bookings where student_id = ? or professor_id = ?",
                UUID.class, userId, userId);
        bookingIds.forEach(this::deleteBookingCascade);

        // Y el resto de su rastro, otra vez de la hoja a la raíz.
        jdbc.update("delete from payout_items where payment_id in (select id from payments where student_id = ? or professor_id = ?)", userId, userId);
        jdbc.update("delete from payments where student_id = ? or professor_id = ?", userId, userId);
        jdbc.update("delete from payouts where professor_id = ?", userId);
        jdbc.update("delete from student_credits where student_id = ? or created_by = ?", userId, userId);
        jdbc.update("delete from messages where sender_id = ?", userId);
        jdbc.update("delete from messages where conversation_id in (select id from conversations where student_id = ? or professor_id = ?)", userId, userId);
        jdbc.update("delete from conversations where student_id = ? or professor_id = ?", userId, userId);
        jdbc.update("delete from notifications where user_id = ?", userId);
        jdbc.update("delete from professor_sanctions where professor_id = ?", userId);
        jdbc.update("delete from professor_absences where professor_id = ?", userId);
        jdbc.update("delete from professor_metrics where professor_id = ?", userId);
        jdbc.update("delete from availability_exceptions where professor_id = ?", userId);
        jdbc.update("delete from availability_rules where professor_id = ?", userId);
        jdbc.update("delete from professor_goals where professor_id = ?", userId);
        jdbc.update("delete from professor_language_levels where professor_id = ?", userId);
        jdbc.update("delete from professor_languages where professor_id = ?", userId);
        jdbc.update("delete from teacher_documents where application_id in (select id from teacher_applications where user_id = ?)", userId);
        jdbc.update("delete from teacher_application_events where application_id in (select id from teacher_applications where user_id = ?)", userId);
        jdbc.update("delete from teacher_applications where user_id = ?", userId);
        jdbc.update("delete from agreement_acceptances where user_id = ?", userId);
        jdbc.update("delete from professor_invites where created_by = ?", userId);
        jdbc.update("delete from password_reset_tokens where user_id = ?", userId);
        jdbc.update("delete from professor_profiles where user_id = ?", userId);
        // La auditoría se conserva: es el registro de lo que OTROS hicieron, y perderlo sería
        // borrar la historia además de la cuenta. Solo se desliga.
        jdbc.update("update admin_audit_log set target_id = null where target_id = ?", userId);
        jdbc.update("delete from admin_audit_log where actor_id = ?", userId);
        jdbc.update("delete from users where id = ?", userId);

        audit.record(admin.getId(), "PURGE_USER", "USER", null,
                preview.label() + " · " + summaryOf(preview) + (reason != null ? " · " + reason : ""));
        log.warn("PURGA del usuario {} por el admin {}", preview.label(), admin.getEmail());
        return preview;
    }

    /* -------------------------------------------------------------------- apoyo */

    private void deleteBookingCascade(UUID bookingId) {
        jdbc.update("delete from payout_items where payment_id in (select id from payments where booking_id = ?)", bookingId);
        jdbc.update("delete from payment_credit_applications where payment_id in (select id from payments where booking_id = ?)", bookingId);
        jdbc.update("delete from payment_events where payment_id in (select id from payments where booking_id = ?)", bookingId);
        jdbc.update("delete from student_credits where booking_id = ?", bookingId);
        jdbc.update("delete from payments where booking_id = ?", bookingId);
        jdbc.update("delete from professor_absences where booking_id = ?", bookingId);
        jdbc.update("delete from reschedule_requests where booking_id = ?", bookingId);
        jdbc.update("delete from disputes where booking_id = ?", bookingId);
        jdbc.update("delete from reviews where booking_id = ?", bookingId);
        jdbc.update("delete from attendance_records where booking_id = ?", bookingId);
        jdbc.update("delete from bookings where id = ?", bookingId);
    }

    private PurgePreview.Money moneyOfBooking(UUID bookingId) {
        return new PurgePreview.Money(
                sum("select coalesce(sum(amount_cop), 0) from payments where booking_id = ?", bookingId),
                sum("""
                    select coalesce(sum(p.professor_earnings_cop), 0) from payments p
                    join payout_items i on i.payment_id = p.id
                    join payouts o on o.id = i.payout_id and o.status = 'PAID'
                    where p.booking_id = ?
                    """, bookingId),
                sum("select coalesce(sum(remaining_cop), 0) from student_credits where booking_id = ?", bookingId));
    }

    private PurgePreview.Money moneyOfUser(UUID userId) {
        return new PurgePreview.Money(
                sum("select coalesce(sum(amount_cop), 0) from payments where student_id = ? or professor_id = ?", userId, userId),
                sum("""
                    select coalesce(sum(p.professor_earnings_cop), 0) from payments p
                    join payout_items i on i.payment_id = p.id
                    join payouts o on o.id = i.payout_id and o.status = 'PAID'
                    where p.student_id = ? or p.professor_id = ?
                    """, userId, userId),
                sum("select coalesce(sum(remaining_cop), 0) from student_credits where student_id = ?", userId));
    }

    private List<String> warningsFor(PurgePreview.Money money, long settledPayments) {
        List<String> warnings = new ArrayList<>();
        if (settledPayments > 0) {
            warnings.add("Incluye " + settledPayments + " pago(s) que YA se liquidaron a un profesor: "
                    + "la contabilidad se queda sin su respaldo.");
        }
        if (money.creditsCop() > 0) {
            warnings.add("Se pierden $" + money.creditsCop() + " de saldo a favor de estudiantes.");
        }
        if (!money.isEmpty()) {
            warnings.add("Esto NO se puede deshacer.");
        }
        return warnings;
    }

    private String describeBooking(UUID bookingId) {
        return jdbc.queryForObject("""
                select coalesce(s.full_name, '?') || ' con ' || coalesce(pr.full_name, '?')
                       || ' · ' || to_char(b.starts_at at time zone 'America/Bogota', 'DD/MM/YYYY HH24:MI')
                       || ' · ' || b.status
                from bookings b
                left join users s on s.id = b.student_id
                left join users pr on pr.id = b.professor_id
                where b.id = ?
                """, String.class, bookingId);
    }

    private void requireBookingExists(UUID bookingId) {
        Long found = jdbc.queryForObject("select count(*) from bookings where id = ?", Long.class, bookingId);
        if (found == null || found == 0) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
    }

    private String summaryOf(PurgePreview preview) {
        return preview.rows().stream()
                .filter(row -> row.count() > 0)
                .map(row -> row.what() + "=" + row.count())
                .reduce((a, b) -> a + ", " + b)
                .orElse("sin filas");
    }

    private PurgePreview.Row row(String what, long count) {
        return new PurgePreview.Row(what, count);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long sum(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
