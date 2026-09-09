package co.orion.lifecycle.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payment;
import co.orion.billing.domain.RefundRequest;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.RefundRequestRepository;
import co.orion.scheduling.application.BookingService;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.time.PlazoLegal;

/**
 * El derecho de retracto del art. 47 de la Ley 1480 de 2011.
 *
 * <p>Vive en {@code lifecycle} porque es el único sitio que mira reserva y pago a la vez, que es
 * exactamente lo que este flujo necesita.
 *
 * <p><strong>Qué es automático y qué no.</strong> Todo menos mover el dinero: comprobar que aplica,
 * cancelar la clase, liberar el cupo, congelar el pago para que nunca llegue al profesor, calcular
 * el vencimiento legal, avisar al estudiante y avisar a administración cuando el plazo se acerca.
 * Lo único manual es la transferencia en el panel de Wompi, porque Wompi no expone reembolso por
 * API — y cerrar la devolución exige escribir su referencia, así que ni eso se puede dar por hecho.
 */
@Service
public class RetractionService {

    private static final Logger log = LoggerFactory.getLogger(RetractionService.class);

    /** Art. 47 de la Ley 1480 de 2011. */
    private static final int DIAS_HABILES_PARA_RETRACTARSE = 5;

    /** Plazo para devolver el dinero, tras la modificación de la Ley 2439 de 2024. */
    private static final int DIAS_CALENDARIO_PARA_DEVOLVER = 15;

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final PaymentRepository payments;
    private final RefundRequestRepository refunds;
    private final RetractionMailer mailer;
    private final Clock clock;

    public RetractionService(BookingRepository bookings,
                             BookingService bookingService,
                             PaymentRepository payments,
                             RefundRequestRepository refunds,
                             RetractionMailer mailer,
                             Clock clock) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.payments = payments;
        this.refunds = refunds;
        this.mailer = mailer;
        this.clock = clock;
    }

    /**
     * Si una reserva admite retracto ahora mismo. Lo consume la tarjeta de la clase para decidir si
     * enseña el botón — y lo vuelve a comprobar {@link #retract} antes de hacer nada.
     */
    @Transactional(readOnly = true)
    public Elegibilidad eligibility(UUID bookingId, UUID studentId) {
        Booking booking = bookings.findById(bookingId)
                .filter(b -> b.getStudentId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        return evaluar(booking, clock.instant());
    }

    /**
     * Ejerce el retracto. No se puede negar: si aplica, aplica.
     *
     * <p>El orden importa. El pago se congela <em>dentro de la misma transacción</em> en la que se
     * cancela la reserva, y no después: el listener de cancelaciones corre AFTER_COMMIT y, al
     * encontrarse un pago que ya no está en PAID, no hace nada. Al revés —cancelar, dejar que el
     * listener actúe y congelar luego— habría abierto una ventana para que el dinero tomara el
     * camino de la política comercial en vez del legal.
     */
    @Transactional
    public RefundRequest retract(UUID bookingId, UUID studentId) {
        Instant now = clock.instant();
        Booking booking = bookings.findById(bookingId)
                .filter(b -> b.getStudentId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        Elegibilidad elegibilidad = evaluar(booking, now);
        if (!elegibilidad.puede()) {
            throw new UnprocessableException(elegibilidad.motivo());
        }
        if (refunds.existsByBookingId(bookingId)) {
            throw new UnprocessableException("Ya hay una devolución en curso para esta clase.");
        }

        Payment payment = payments.findByBookingId(bookingId)
                .orElseThrow(() -> new UnprocessableException(
                        "Esta clase no tiene un pago que devolver."));

        // Congelar primero: a partir de aquí el dinero ya no puede irse por otro camino.
        payment.startRefund();
        payments.save(payment);

        RefundRequest solicitud = refunds.save(new RefundRequest(
                bookingId, payment.getId(), studentId, RefundRequest.Reason.RETRACTO,
                payment.getAmountCop(),
                PlazoLegal.sumandoDiasCalendario(now, DIAS_CALENDARIO_PARA_DEVOLVER)));

        bookingService.cancelForRetraction(bookingId, studentId, now);

        mailer.confirmarRetracto(studentId, payment.getAmountCop(), solicitud.getDueAt());
        log.info("Retracto ejercido sobre la reserva {}: {} COP a devolver antes de {}",
                bookingId, payment.getAmountCop(), solicitud.getDueAt());
        return solicitud;
    }

    /** Las devoluciones pendientes, lo que vence antes primero. */
    @Transactional(readOnly = true)
    public List<RefundRequest> pendientes() {
        return refunds.findByStatusOrderByDueAtAsc(RefundRequest.Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<RefundRequest> mias(UUID studentId) {
        return refunds.findByStudentIdOrderByRequestedAtDesc(studentId);
    }

    /**
     * Administración confirma la devolución hecha en Wompi. Cierra el pago y la solicitud a la vez:
     * dejar una de las dos abierta haría que la siguiente persona la volviera a pagar.
     */
    @Transactional
    public RefundRequest confirmarDevolucion(UUID refundId, String referencia, String nota,
                                             UUID actorId) {
        Instant now = clock.instant();
        RefundRequest solicitud = refunds.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Devolución no encontrada"));

        solicitud.markPaid(referencia, nota, actorId, now);
        refunds.save(solicitud);

        Payment payment = payments.findById(solicitud.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("El pago ya no existe"));
        payment.refund(now);
        payments.save(payment);

        mailer.confirmarDevolucion(solicitud.getStudentId(), solicitud.getAmountCop(),
                solicitud.getWompiReference());
        return solicitud;
    }

    private Elegibilidad evaluar(Booking booking, Instant now) {
        if (booking.getStatus().isTerminal()) {
            return Elegibilidad.no("Esta clase ya no está activa.");
        }
        if (!booking.isConfirmed()) {
            return Elegibilidad.no("Esta clase todavía no está pagada; puedes soltar el cupo.");
        }
        // La excepción del art. 47: no hay retracto sobre un servicio cuya prestación ya comenzó
        // con acuerdo del consumidor. Una clase que ya empezó es exactamente ese caso.
        if (!booking.getStartsAt().isAfter(now)) {
            return Elegibilidad.no("La clase ya empezó, así que el retracto ya no aplica.");
        }

        Instant limite = PlazoLegal.sumandoDiasHabiles(
                booking.getCreatedAt(), DIAS_HABILES_PARA_RETRACTARSE);
        if (now.isAfter(limite)) {
            return Elegibilidad.no(
                    "El plazo de retracto es de 5 días hábiles desde que reservaste y ya pasó. "
                            + "Puedes cancelar si aún estás a tiempo.");
        }
        return new Elegibilidad(true, null, limite);
    }

    /** Si se puede, por qué no, y hasta cuándo se podría. */
    public record Elegibilidad(boolean puede, String motivo, Instant limite) {

        static Elegibilidad no(String motivo) {
            return new Elegibilidad(false, motivo, null);
        }
    }
}
