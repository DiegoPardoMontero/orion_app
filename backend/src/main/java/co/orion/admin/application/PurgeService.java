package co.orion.admin.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * Se instancia aquí en vez de inyectarse: en Boot 4 no hay un bean de {@code ObjectMapper} que
     * pedir. Es apátrida y solo serializa tres claves, así que no hay nada que compartir.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

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
                        count("select count(*) from professor_absences where booking_id = ?", bookingId)),
                // Se van por la FK en cascada, pero el acta es texto que escribió el profesor: quien
                // confirma el borrado tiene que verla en la lista.
                row("Acta de la clase",
                        count("select count(*) from lesson_notes where booking_id = ?", bookingId)),
                row("Práctica de esa acta",
                        count("select count(*) from practice_sets s join lesson_notes n on n.id = s.lesson_note_id "
                              + "where n.booking_id = ?", bookingId)));

        PurgePreview.Money money = moneyOfBooking(bookingId);
        return new PurgePreview("booking", describeBooking(bookingId), rows, money,
                warningsFor(money, count("select count(*) from payout_lines i join payments p "
                        + "on p.id = i.payment_id where p.booking_id = ?", bookingId)));
    }

    @Transactional
    public PurgePreview purgeBooking(UUID bookingId, User admin, String reason) {
        PurgePreview preview = previewBooking(bookingId);

        // De la hoja a la raíz: nada apunta a lo que se borra cuando le llega el turno.
        jdbc.update("delete from payout_lines where booking_id = ? or payment_id in (select id from payments where booking_id = ?)", bookingId, bookingId);
        jdbc.update("delete from payout_adjustments where booking_id = ?", bookingId);
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
                detalle(preview.label(), summaryOf(preview), reason));
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
                        count("select count(*) from teacher_applications where user_id = ?", userId)),
                row("Actas de clase escritas o recibidas",
                        count("select count(*) from lesson_notes where student_id = ? or professor_id = ?", userId, userId)),
                row("Sets de práctica",
                        count("select count(*) from practice_sets where student_id = ? or professor_id = ?", userId, userId)));

        PurgePreview.Money money = moneyOfUser(userId);
        List<String> warnings = new ArrayList<>(warningsFor(money,
                count("select count(*) from payout_lines i join payments p on p.id = i.payment_id "
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
        jdbc.update("delete from payout_lines where payment_id in (select id from payments where student_id = ? or professor_id = ?)", userId, userId);
        jdbc.update("delete from payments where student_id = ? or professor_id = ?", userId, userId);
        jdbc.update("delete from payout_adjustments where professor_id = ?", userId);
        jdbc.update("delete from payouts where professor_id = ?", userId);
        jdbc.update("delete from professor_payout_details where professor_id = ?", userId);
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
        // `user_id`, que es el invitado. NUNCA existió una columna `created_by` aquí: esta línea
        // llevaba tiempo lanzando «column does not exist», y como es la misma transacción, tumbaba
        // la purga ENTERA — de cualquier usuario, tuviera historia o no. El admin veía «Unexpected
        // error» y nada más. No había un solo test de la purga; ahora sí.
        jdbc.update("delete from professor_invites where user_id = ?", userId);

        // --- Lo que los bloques 8 y 9 fueron colgando de `users` y esta lista no sabía ---
        //
        // La purga borra a mano, tabla por tabla. Eso significa que cada tabla nueva que apunte a
        // una cuenta hay que añadirla aquí, y las que faltaban solo se notaban al intentar borrar
        // a alguien que las hubiera tocado.

        // Soporte (V27). El ticket propio cae por ON DELETE CASCADE, pero un mensaje suyo dentro
        // del hilo de OTRA persona no: ese hilo sigue vivo y no puede quedarse con un autor que ya
        // no existe.
        jdbc.update("delete from support_messages where author_id = ?", userId);

        // Ajustes (V28). El valor actual solo pierde la firma de quién lo puso; el historial de
        // cambios no puede: `changed_by` es NOT NULL, así que esas filas se van, igual que se va la
        // bitácora de este admin unas líneas más abajo.
        jdbc.update("update platform_settings set updated_by = null where updated_by = ?", userId);
        jdbc.update("delete from platform_setting_changes where changed_by = ?", userId);

        // Retracto (V29). Las devoluciones del estudiante son suyas y se van con él; la firma de
        // quien las resolvió, no — esa devolución puede ser de otra persona.
        jdbc.update("delete from refund_requests where student_id = ?", userId);
        jdbc.update("update refund_requests set resolved_by = null where resolved_by = ?", userId);

        // Diagnóstico: el gasto de IA (se desliga) y las recomendaciones de este profesor en
        // resultados ajenos (se borran) los resuelve la base desde la V47, con sus ON DELETE.

        // Las firmas ajenas: lo que este usuario decidió SOBRE otras cuentas. La decisión se
        // conserva —es historia de esa otra persona— y solo pierde el nombre de quien la tomó.
        jdbc.update("update disputes set resolved_by = null where resolved_by = ?", userId);
        jdbc.update("update professor_sanctions set created_by = null where created_by = ?", userId);
        jdbc.update("update professor_sanctions set revoked_by = null where revoked_by = ?", userId);
        jdbc.update("update teacher_applications set reviewed_by = null where reviewed_by = ?", userId);
        jdbc.update("update teacher_application_events set actor_id = null where actor_id = ?", userId);
        jdbc.update("update bookings set cancelled_by = null where cancelled_by = ?", userId);
        // `created_by` es la reserva que este admin hizo en nombre de un estudiante que NO se está
        // borrando. Se queda sin firma (V32) en vez de llevarse por delante la clase de un tercero.
        jdbc.update("update bookings set created_by = null where created_by = ?", userId);
        jdbc.update("delete from password_reset_tokens where user_id = ?", userId);
        jdbc.update("delete from professor_profiles where user_id = ?", userId);
        // La bitácora del admin. La columna se llama `entity_id` —`target_id` nunca existió, y era
        // la segunda de las dos consultas escritas contra un esquema imaginado— y no tiene FK, así
        // que no bloquea nada: se desliga igual, porque un id que ya no apunta a nadie es ruido.
        //
        // Las líneas donde este usuario fue el ACTOR sí se van: `actor_id` es NOT NULL y tiene FK.
        // Se pierde el registro de lo que hizo, que es el precio de borrar definitivamente a quien
        // lo hizo.
        jdbc.update("update admin_audit_log set entity_id = null "
                + "where entity_type = 'USER' and entity_id = ?", userId);
        jdbc.update("delete from admin_audit_log where actor_id = ?", userId);
        jdbc.update("delete from users where id = ?", userId);

        audit.record(admin.getId(), "PURGE_USER", "USER", null,
                detalle(preview.label(), summaryOf(preview), reason));
        log.warn("PURGA del usuario {} por el admin {}", preview.label(), admin.getEmail());
        return preview;
    }

    /* -------------------------------------------------------------------- apoyo */

    /**
     * El detalle de la bitácora, como JSON.
     *
     * <p>`admin_audit_log.detail` es JSONB y las dos purgas le pasaban una frase en castellano
     * («Ana Ramírez · 3 clases, 1 pago…»). Postgres la rechazaba, y como la auditoría es la
     * ÚLTIMA línea de las dos, la transacción entera reventaba después de haber borrado todo: nadie
     * llegó nunca a ver el commit. Se serializa con Jackson y no a mano, porque escapar comillas a
     * mano es exactamente cómo vuelve este bug.
     */
    private String detalle(String label, String resumen, String reason) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("objetivo", label);
        campos.put("resumen", resumen);
        if (reason != null && !reason.isBlank()) {
            campos.put("motivo", reason.trim());
        }
        try {
            return JSON.writeValueAsString(campos);
        } catch (JsonProcessingException ex) {
            // Que la auditoría no pueda serializarse no puede impedir la purga; queda constancia
            // mínima y el porqué en el log.
            log.error("No se pudo serializar el detalle de la purga de {}", label, ex);
            return "{}";
        }
    }

    private void deleteBookingCascade(UUID bookingId) {
        jdbc.update("delete from payout_lines where booking_id = ? or payment_id in (select id from payments where booking_id = ?)", bookingId, bookingId);
        jdbc.update("delete from payout_adjustments where booking_id = ?", bookingId);
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
                    join payout_lines i on i.payment_id = p.id
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
                    join payout_lines i on i.payment_id = p.id
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
