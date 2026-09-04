package co.orion.scheduling.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una clase reservada. Como en el resto de scheduling, las referencias a usuarios son UUIDs
 * planos y no relaciones JPA: la integridad la garantizan las FK, no el grafo de objetos.
 *
 * La columna package_id existe en la tabla (reservada para los paquetes del MVP 2) pero no se
 * mapea aquí: nada la usa todavía, y Hibernate solo valida los atributos que sí declaramos.
 */
@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private BookingModality modality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "location_note", length = 300)
    private String locationNote;

    /** Sala de videollamada para clases VIRTUAL (Jitsi hoy). Null en presenciales. */
    @Column(name = "meeting_link", length = 300)
    private String meetingLink;

    /**
     * En qué idioma se da la clase. Nulo solo en las reservas anteriores a la V20 cuyo profesor
     * enseñaba más de uno: ahí no se puede deducir, y "no lo sabemos" es la verdad. En las nuevas
     * siempre está — el servicio lo asigna o lo exige.
     */
    @Column(name = "language_code", length = 5)
    private String languageCode;

    /** Clase de prueba (Q7). La columna existe desde la V16; el flujo que la enciende no. */
    @Column(name = "is_trial", nullable = false)
    private boolean trial;

    /**
     * Hasta cuándo se le guarda el cupo al estudiante mientras paga. Solo tiene sentido en
     * PENDING_PAYMENT: al confirmar se borra, porque una clase pagada ya no vence.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Quién ejecutó la acción. Si coincide con student_id, la reserva fue autoservicio. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    /**
     * Cuándo se cerró la clase. Es lo que hace idempotente al job de autocompletado: una reserva
     * con esta marca ya se cerró, aunque el job vuelva a pasar por ella.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
        // exigido por JPA
    }

    /**
     * Una reserva nace SIN pagar y con fecha de caducidad. No hay forma de construir una reserva
     * ya confirmada: la única puerta a CONFIRMED es {@link #confirmPayment()}, y así ninguna clase
     * llega a existir sin que alguien haya mirado el dinero.
     */
    public Booking(UUID studentId,
                   UUID professorId,
                   Instant startsAt,
                   Instant endsAt,
                   BookingModality modality,
                   String locationNote,
                   String languageCode,
                   UUID createdBy,
                   Instant expiresAt) {
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        this.modality = Objects.requireNonNull(modality, "modality");
        this.locationNote = locationNote;
        this.languageCode = languageCode;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    /** Solo para completar a mano una reserva histórica sin idioma, desde administración. */
    public void assignLanguage(String languageCode) {
        this.languageCode = Objects.requireNonNull(languageCode, "languageCode");
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    public boolean isAwaitingPayment() {
        return status == BookingStatus.PENDING_PAYMENT;
    }

    /** El pago entró (o el crédito cubrió la clase entera): la clase existe de verdad. */
    public void confirmPayment() {
        if (!isAwaitingPayment()) {
            throw new IllegalStateException("Solo una reserva PENDING_PAYMENT se puede confirmar");
        }
        this.status = BookingStatus.CONFIRMED;
        this.expiresAt = null;
    }

    /** Se acabó el plazo (o la pasarela rechazó): el cupo vuelve al mercado. */
    public void expire() {
        if (!isAwaitingPayment()) {
            throw new IllegalStateException("Solo una reserva PENDING_PAYMENT vence");
        }
        this.status = BookingStatus.EXPIRED;
    }

    /** ¿Se le acabó el plazo para pagar? */
    public boolean hasPaymentExpiredAt(Instant now) {
        return isAwaitingPayment() && expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * ¿Queda margen para cancelar? La ventana entra por parámetro y no vive aquí: el estudiante y
     * el profesor tienen la suya, ambas configurables en {@code platform_settings}, y una constante
     * en el dominio obligaría a desplegar para cambiar una política de negocio.
     *
     * La comparación es entre instantes, no entre horas de pared, así que la zona horaria no
     * interviene — la distancia entre dos momentos es la misma vista desde Bogotá o desde Tokio.
     *
     * El ADMIN está exento, pero esa excepción vive en el servicio: es una regla sobre QUIÉN
     * cancela, no sobre la reserva misma.
     */
    public boolean isCancellableAt(Instant now, Duration window) {
        // Una reserva sin pagar se suelta siempre: la ventana protege una clase que ya existe, y
        // ésta todavía no lo es. Nadie contaba con esa hora.
        if (isAwaitingPayment()) {
            return true;
        }
        return isConfirmed() && !startsAt.minus(window).isBefore(now);
    }

    /** Autoservicio: la reservó el propio estudiante, no un admin en su nombre. */
    public boolean isSelfService() {
        return createdBy.equals(studentId);
    }

    /** ¿Ya terminó la clase? Comparación entre instantes: la zona horaria no interviene. */
    public boolean hasEndedAt(Instant now) {
        return !endsAt.isAfter(now);
    }

    /**
     * Cierra la clase con el resultado de la asistencia DEL ESTUDIANTE. Es la otra salida de
     * CONFIRMED, junto a la cancelación: la reserva no se queda confirmada para siempre después de
     * ocurrir. En los dos desenlaces el profesor cobra — apartó su hora y estuvo ahí.
     */
    public void closeWithAttendance(boolean present, Instant now) {
        if (!isConfirmed()) {
            throw new IllegalStateException("Solo una reserva CONFIRMED admite registro de asistencia");
        }
        this.status = present ? BookingStatus.COMPLETED : BookingStatus.NO_SHOW_STUDENT;
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * El cierre automático: pasaron las horas de gracia, nadie reclamó y la clase se da por dictada.
     * Devuelve false si ya estaba cerrada, que es como el job puede correr dos veces sin liberar
     * el mismo pago dos veces.
     */
    public boolean autoComplete(Instant now) {
        if (completedAt != null || !isConfirmed()) {
            return false;
        }
        this.status = BookingStatus.COMPLETED;
        this.completedAt = now;
        return true;
    }

    /** El estudiante abrió un reclamo: la clase no se cierra sola hasta que alguien lo resuelva. */
    public void putUnderReview() {
        if (!isConfirmed()) {
            throw new IllegalStateException("Solo una reserva CONFIRMED admite un reclamo");
        }
        this.status = BookingStatus.UNDER_REVIEW;
    }

    /** El reclamo se resolvió: la clase contó (a favor del profesor) o no (ausencia suya). */
    public void resolveReview(boolean lessonHeld, Instant now) {
        if (status != BookingStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Esta reserva no está en revisión");
        }
        this.status = lessonHeld ? BookingStatus.COMPLETED : BookingStatus.NO_SHOW_PROFESSOR;
        this.completedAt = now;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * Transición a un estado terminal de cancelación, desde CONFIRMED o desde PENDING_PAYMENT.
     * Los demás estados son finales: volver a cancelar algo ya cancelado es un conflicto, no una
     * operación idempotente. Quién puede cancelar y cuándo lo decide el servicio; aquí solo se
     * guarda el hecho.
     */
    public void cancel(BookingStatus cancelledStatus, UUID cancelledBy, Instant cancelledAt, String reason) {
        // También desde PENDING_PAYMENT: arrepentirse antes de pagar es cancelar, no un error.
        if (status.isTerminal()) {
            throw new IllegalStateException("Una reserva en estado terminal no se puede cancelar");
        }
        this.status = Objects.requireNonNull(cancelledStatus, "cancelledStatus");
        this.cancelledBy = Objects.requireNonNull(cancelledBy, "cancelledBy");
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt");
        this.cancellationReason = reason;
    }

    /**
     * Mueve la clase a otro horario, conservando id, modalidad y nota. Solo desde CONFIRMED: una
     * clase cancelada o ya impartida no se reprograma. Quién puede reprogramar y con cuánta
     * antelación lo decide el servicio; aquí solo se guarda el nuevo instante.
     */
    public void reschedule(Instant newStartsAt, Instant newEndsAt) {
        if (!isConfirmed()) {
            throw new IllegalStateException("Solo una reserva CONFIRMED se puede reprogramar");
        }
        this.startsAt = Objects.requireNonNull(newStartsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(newEndsAt, "endsAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BookingModality getModality() {
        return modality;
    }

    public BookingStatus getStatus() {
        return status;
    }

    /** Asigna la sala de videollamada. Se hace tras el INSERT (el link depende del id generado). */
    public void assignMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public boolean isTrial() {
        return trial;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getLocationNote() {
        return locationNote;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
