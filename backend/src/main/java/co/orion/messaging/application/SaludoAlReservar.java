package co.orion.messaging.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.StudentGoal;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.StudentGoalRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.messaging.domain.Conversation;
import co.orion.messaging.domain.Message;
import co.orion.messaging.persistence.ConversationRepository;
import co.orion.messaging.persistence.MessageRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.FechasEnPalabras;

/**
 * El saludo al reservar (decisión de Pardo, 24/09/2026): cuando una clase queda agendada, al
 * estudiante le llega en su chat con el profe un mensaje <strong>a nombre del profe</strong>, que
 * saluda, recuerda el día y la hora y dice quién lo saluda. Lo escribe Orión y la pantalla lo dice
 * («Enviado por Orión»): el profe no lo escribió a mano, pero habla por él.
 *
 * <p>No es genérico: distingue la primera clase juntos de las siguientes, y si el estudiante dijo en
 * su ficha para qué quiere el idioma, lo menciona. Lleva su ⭐.
 *
 * <p>Uno por reserva, y lo garantiza la base (índice único por {@code booking_id}): si el evento se
 * repite, el segundo saludo no entra. Sale después del commit de la reserva: un saludo nunca anuncia
 * una clase que hizo rollback, y si falla, la reserva sigue en pie.
 */
@Service
public class SaludoAlReservar {

    private static final Logger log = LoggerFactory.getLogger(SaludoAlReservar.class);

    /** Para qué lo quiere, dicho como se dice en una frase. Sin «aprendizaje general»: no dice nada. */
    private static final Map<String, String> OBJETIVO = Map.of(
            "CONVERSATION", "soltarte a conversar",
            "TRAVEL", "tus viajes",
            "BUSINESS", "tu trabajo",
            "ACADEMIC", "lo académico",
            "EXAMS", "un examen",
            "INTERVIEW", "una entrevista");

    private final BookingRepository bookings;
    private final UserRepository users;
    private final StudentGoalRepository goals;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaccion;

    public SaludoAlReservar(BookingRepository bookings, UserRepository users, StudentGoalRepository goals,
                            ConversationRepository conversations, MessageRepository messages,
                            ApplicationEventPublisher events, PlatformTransactionManager transacciones) {
        this.bookings = bookings;
        this.users = users;
        this.goals = goals;
        this.conversations = conversations;
        this.messages = messages;
        this.events = events;
        // Una transacción propia y explícita: la llamada desde el listener es interna a la clase y
        // se saltaría un @Transactional (la trampa de los jobs del 23/09).
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        try {
            transaccion.executeWithoutResult(estado -> saludar(event.bookingId()));
        } catch (DataIntegrityViolationException ex) {
            // Ya había saludo para esta reserva: el evento se repitió. No es un error.
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el saludo de la reserva {}: {}", event.bookingId(), ex.getMessage());
        }
    }

    /** Escribe el saludo, si la reserva sigue en pie y no tiene uno. Corre dentro de una transacción. */
    void saludar(UUID bookingId) {
        Booking reserva = bookings.findById(bookingId).orElse(null);
        if (reserva == null || reserva.getStatus() != BookingStatus.CONFIRMED || reserva.isRehearsal()
                || messages.existsByBookingIdAndAutomatedTrue(bookingId)) {
            return;
        }
        User estudiante = users.findById(reserva.getStudentId()).orElse(null);
        User profe = users.findById(reserva.getProfessorId()).orElse(null);
        if (estudiante == null || profe == null) {
            return;
        }
        boolean primera = bookings.countEarlierTogether(reserva.getStudentId(), reserva.getProfessorId(),
                reserva.getCreatedAt(), List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED)) == 0;
        String cuerpo = reserva.isTrial()
                ? textoDePrueba(FechasEnPalabras.primerNombre(estudiante.getFullName()),
                        FechasEnPalabras.primerNombre(profe.getFullName()),
                        FechasEnPalabras.dia(reserva.getStartsAt()), FechasEnPalabras.hora(reserva.getStartsAt()),
                        objetivoDe(estudiante.getId()))
                : texto(FechasEnPalabras.primerNombre(estudiante.getFullName()),
                        FechasEnPalabras.primerNombre(profe.getFullName()),
                        FechasEnPalabras.dia(reserva.getStartsAt()), FechasEnPalabras.hora(reserva.getStartsAt()),
                        primera, objetivoDe(estudiante.getId()));

        Conversation conversacion = conversations
                .findByStudentIdAndProfessorId(reserva.getStudentId(), reserva.getProfessorId())
                .orElseGet(() -> conversations.save(new Conversation(reserva.getStudentId(), reserva.getProfessorId())));
        Message saludo = messages.saveAndFlush(
                Message.automatic(conversacion.getId(), profe.getId(), reserva.getId(), cuerpo));
        conversacion.touch(saludo.getCreatedAt() != null ? saludo.getCreatedAt() : reserva.getCreatedAt());
        conversations.save(conversacion);
        // Va a la campana del estudiante como cualquier mensaje (MessageDelivery no le manda correo).
        events.publishEvent(new MessagePostedEvent(saludo.getId()));
    }

    /**
     * El texto. Primera clase juntos o no, y el objetivo de la ficha si lo hay.
     *
     * @param objetivo para qué quiere el idioma, ya dicho en frase («tus viajes»), o {@code null}
     */
    static String texto(String estudiante, String profe, String dia, String hora, boolean primera, String objetivo) {
        StringBuilder t = new StringBuilder();
        if (primera) {
            t.append("¡Hola, ").append(estudiante).append("! ⭐ Soy ").append(profe)
                    .append(", tu profe en Orión. Te confirmo nuestra primera clase: el ").append(dia)
                    .append(" a las ").append(hora).append(" (hora de Colombia).");
            if (objetivo != null) {
                t.append(" Vi en tu ficha que lo quieres para ").append(objetivo)
                        .append(": lo tengo en cuenta para prepararla.");
            }
            t.append(" Si quieres contarme algo antes de empezar, escríbeme por aquí. ¡Nos vemos en el aula!");
        } else {
            t.append("¡Hola de nuevo, ").append(estudiante).append("! ⭐ Soy ").append(profe)
                    .append(". Ya quedó agendada nuestra próxima clase: el ").append(dia).append(" a las ")
                    .append(hora).append(" (hora de Colombia). Si hay algo que quieras repasar de la última,"
                            + " cuéntamelo por aquí. ¡Nos vemos!");
        }
        return t.toString();
    }

    /** El de la clase de prueba: es para conocerse, y el saludo lo dice. */
    static String textoDePrueba(String estudiante, String profe, String dia, String hora, String objetivo) {
        StringBuilder t = new StringBuilder();
        t.append("¡Hola, ").append(estudiante).append("! ⭐ Soy ").append(profe)
                .append(". Te confirmo nuestra clase de prueba: el ").append(dia).append(" a las ").append(hora)
                .append(" (hora de Colombia). Es para conocernos: veremos tu nivel y lo que buscas");
        if (objetivo != null) {
            t.append(" —vi en tu ficha que lo quieres para ").append(objetivo).append("—");
        }
        t.append(", y te cuento cómo trabajaría contigo. Si quieres adelantarme algo, escríbeme por aquí. ¡Nos vemos!");
        return t.toString();
    }

    private String objetivoDe(UUID estudianteId) {
        return goals.findByUserId(estudianteId).stream()
                .map(StudentGoal::getGoalCode)
                .map(OBJETIVO::get)
                .filter(o -> o != null)
                .findFirst()
                .orElse(null);
    }
}
