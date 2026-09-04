package co.orion.messaging.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.application.DisputeOpened;
import co.orion.lifecycle.application.DisputeResolved;
import co.orion.lifecycle.persistence.DisputeRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.RescheduleRequest;
import co.orion.scheduling.domain.RescheduleRequested;
import co.orion.scheduling.domain.RescheduleResolved;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.RescheduleRequestRepository;
import co.orion.shared.time.BusinessZone;

/**
 * Avisos in-app del ciclo de vida de una clase.
 *
 * Una propuesta de cambio que nadie ve es una clase que nadie mueve, y un reclamo que el admin no
 * ve es dinero congelado sin que nadie lo sepa. Por eso los cuatro hechos notifican.
 *
 * {@code AFTER_COMMIT} + {@code REQUIRES_NEW}, como el resto: nunca se avisa de algo que hizo
 * rollback, y un fallo aquí no tumba la operación que lo provocó.
 */
@Component
public class LifecycleNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LifecycleNotificationListener.class);

    private final NotificationService notifications;
    private final RescheduleRequestRepository requests;
    private final DisputeRepository disputes;
    private final BookingRepository bookings;
    private final UserRepository users;

    public LifecycleNotificationListener(NotificationService notifications,
                                         RescheduleRequestRepository requests,
                                         DisputeRepository disputes,
                                         BookingRepository bookings,
                                         UserRepository users) {
        this.notifications = notifications;
        this.requests = requests;
        this.disputes = disputes;
        this.bookings = bookings;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRescheduleRequested(RescheduleRequested event) {
        safely(() -> requests.findById(event.requestId()).ifPresent(request ->
                bookings.findById(request.getBookingId()).ifPresent(booking -> {
                    UUID target = counterpartOf(booking, request.getRequestedBy());
                    notifications.create(target, "RESCHEDULE_REQUESTED",
                            "Te proponen cambiar el horario de una clase",
                            "Nueva propuesta: " + whenIs(request) + ". Acéptala o propón otra hora.",
                            claseFutura(booking.getId()));
                })));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRescheduleResolved(RescheduleResolved event) {
        safely(() -> requests.findById(event.requestId()).ifPresent(request ->
                bookings.findById(request.getBookingId()).ifPresent(booking -> {
                    // Le avisamos a quien propuso: es quien está esperando respuesta.
                    notifications.create(request.getRequestedBy(),
                            event.accepted() ? "RESCHEDULE_ACCEPTED" : "RESCHEDULE_DECLINED",
                            event.accepted()
                                    ? "Aceptaron tu cambio de horario"
                                    : "No aceptaron tu cambio de horario",
                            event.accepted()
                                    ? "Tu clase quedó para " + whenIs(request) + "."
                                    : "La clase sigue a su hora original. Puedes proponer otro horario.",
                            claseFutura(booking.getId()));
                })));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDisputeOpened(DisputeOpened event) {
        safely(() -> disputes.findById(event.disputeId()).ifPresent(dispute ->
                bookings.findById(dispute.getBookingId()).ifPresent(booking -> {
                    // Al profesor, porque le afecta y merece enterarse por Orión y no por sorpresa.
                    notifications.create(booking.getProfessorId(), "DISPUTE_OPENED",
                            "Un estudiante reportó un problema con una clase",
                            "Estamos revisando lo ocurrido. Te contamos apenas se resuelva.",
                            clasePasada(booking.getId()));
                    // Y a cada admin, porque es dinero congelado esperando una decisión.
                    users.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE).forEach(admin ->
                            notifications.create(admin.getId(), "DISPUTE_OPENED",
                                    "Nuevo reclamo por resolver",
                                    "Hay un reclamo abierto con dinero retenido.",
                                    "/admin/reclamos"));
                })));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDisputeResolved(DisputeResolved event) {
        safely(() -> disputes.findById(event.disputeId()).ifPresent(dispute ->
                bookings.findById(dispute.getBookingId()).ifPresent(booking -> {
                    notifications.create(booking.getStudentId(), "DISPUTE_RESOLVED",
                            "Resolvimos tu reclamo",
                            event.absenceRecorded()
                                    ? "Te devolvimos el valor de la clase como saldo a favor."
                                    : "Revisamos el caso y la clase quedó registrada como dictada.",
                            "/saldo");
                    notifications.create(booking.getProfessorId(), "DISPUTE_RESOLVED",
                            "Se resolvió el reclamo de una clase",
                            event.absenceRecorded()
                                    ? "Quedó registrada como ausencia. Puedes ver el detalle en tu desempeño."
                                    : "La clase quedó como dictada y tu pago sigue su curso.",
                            "/ganancias");
                })));
    }

    /** El hecho ya ocurrió y está guardado: que falle un aviso no puede deshacerlo. */
    /**
     * Un aviso sobre una clase concreta lleva a esa clase, no a la lista.
     *
     * <p>Llevar a «Mis clases» a secas obliga a buscar de cuál se hablaba, y quien tiene seis
     * clases esta semana no lo adivina. El {@code scope} viaja porque aquí sí se sabe: una
     * propuesta de horario siempre es sobre una clase futura y un reclamo siempre sobre una que ya
     * pasó, así que el enlace abre la pestaña correcta sin que el frontend tenga que averiguarlo.
     */
    private String claseFutura(UUID bookingId) {
        return "/mis-clases?clase=" + bookingId;
    }

    private String clasePasada(UUID bookingId) {
        return "/mis-clases?scope=past&clase=" + bookingId;
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error("No se pudo crear una notificación del ciclo de vida", ex);
        }
    }

    private UUID counterpartOf(Booking booking, UUID actorId) {
        return booking.getStudentId().equals(actorId)
                ? booking.getProfessorId()
                : booking.getStudentId();
    }

    private String whenIs(RescheduleRequest request) {
        return request.getProposedStartsAt().atZone(BusinessZone.BOGOTA)
                .format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' HH:mm",
                        java.util.Locale.forLanguageTag("es-CO")));
    }
}
