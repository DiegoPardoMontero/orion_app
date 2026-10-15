package co.orion.support.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.MinorsClearanceService;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.observability.AlertService;
import co.orion.shared.time.FechasEnPalabras;
import co.orion.support.domain.TicketCategory;

/**
 * «Reportar un problema de seguridad» (Bloque 12, propuesta aprobada): el adulto lo pulsa en la clase
 * de su adolescente y pasan tres cosas a la vez: se abre un ticket de soporte con la prioridad de la
 * casa (el equipo contacta a la familia en menos de 24 horas), el profe queda inhabilitado para
 * menores de inmediato mientras se revisa, y el equipo recibe la alerta. La evidencia (mensajes y
 * registros de la clase) no se borra: la purga de habeas data la conserva como soporte del reclamo.
 */
@Service
public class SafetyReportService {

    private static final Logger log = LoggerFactory.getLogger(SafetyReportService.class);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final SupportService support;
    private final MinorsClearanceService minors;
    private final AlertService alertas;

    public SafetyReportService(BookingRepository bookings, UserRepository users, SupportService support,
                               MinorsClearanceService minors, AlertService alertas) {
        this.bookings = bookings;
        this.users = users;
        this.support = support;
        this.minors = minors;
        this.alertas = alertas;
    }

    @Transactional
    public SupportService.Hilo reportar(User actor, UUID bookingId, String body) {
        Booking booking = bookings.findById(bookingId)
                .filter(b -> b.getStudentId().equals(actor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        if (!booking.isForAdolescent()) {
            throw new UnprocessableException(
                    "El reporte de seguridad es para las clases de un adolescente. Para lo demás está «Reportar un problema».");
        }
        if (body == null || body.strip().length() < 10) {
            throw new UnprocessableException("Cuéntanos qué pasó, aunque sea en una frase.");
        }
        String profe = users.findById(booking.getProfessorId()).map(User::getFullName).orElse("el profesor");
        String asunto = "Seguridad: clase del " + FechasEnPalabras.diaCorto(booking.getStartsAt()) + " con " + profe;
        SupportService.Hilo hilo = support.abrir(actor.getId(), TicketCategory.SEGURIDAD_MENOR,
                asunto.length() > 160 ? asunto.substring(0, 160) : asunto, body.strip(), bookingId);

        // Inhabilitación inmediata, mientras se revisa. Quien la vuelve a encender es el admin, a mano.
        minors.revoke(booking.getProfessorId(), actor.getId());

        alertas.alert("seguridad-menor-" + bookingId,
                "Reporte de seguridad en la clase de un adolescente (" + hilo.ticket().getCode() + ")",
                "Un representante legal reportó un problema de seguridad en una clase con " + profe
                        + ". El profe quedó inhabilitado para adolescentes mientras se revisa. Contacta a la familia en"
                        + " menos de 24 horas: Administración → Soporte → " + hilo.ticket().getCode() + ".");
        log.warn("Reporte de seguridad {} sobre la clase {}: el profesor {} queda inhabilitado para menores",
                hilo.ticket().getCode(), bookingId, booking.getProfessorId());
        return hilo;
    }
}
