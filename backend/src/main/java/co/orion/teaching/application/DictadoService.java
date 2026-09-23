package co.orion.teaching.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.ServiceUnavailableException;
import co.orion.shared.error.UnprocessableException;

/**
 * El profesor dicta en vez de escribir (añadido por Pardo al Bloque 10 el 22/09/2026; el brief lo
 * dejaba fuera).
 *
 * <p>Las mismas puertas que escribir el acta: solo el profesor de la clase, solo una clase cerrada
 * desde que existen las actas. Así el dictado no es un transcriptor gratis para cualquier audio.
 */
@Service
public class DictadoService {

    /** Más que esto no es «lo que se te venga a la cabeza»: es una clase entera grabada. */
    public static final int MAX_SEGUNDOS = 180;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final BookingRepository bookings;
    private final LessonNotesLaunch lanzamiento;
    private final TeachingAiBudget presupuesto;
    private final TranscriptorDeDictado transcriptor;

    public DictadoService(BookingRepository bookings, LessonNotesLaunch lanzamiento,
                          TeachingAiBudget presupuesto, TranscriptorDeDictado transcriptor) {
        this.bookings = bookings;
        this.lanzamiento = lanzamiento;
        this.presupuesto = presupuesto;
        this.transcriptor = transcriptor;
    }

    public String dictar(User profesor, UUID bookingId, byte[] audio, String tipo, int segundos) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Clase no encontrada"));
        if (!b.getProfessorId().equals(profesor.getId())) {
            throw new ForbiddenException("Solo el profesor de esta clase puede dictar su acta.");
        }
        if (b.getStatus() != BookingStatus.COMPLETED || b.getCompletedAt() == null
                || b.getCompletedAt().isBefore(lanzamiento.desde())) {
            throw new UnprocessableException("El acta se dicta cuando la clase ya se dictó y quedó cerrada.");
        }
        if (audio == null || audio.length == 0 || tipo == null
                || !(tipo.startsWith("audio/") || tipo.startsWith("video/webm"))) {
            throw new UnprocessableException("No recibimos el audio. Intenta de nuevo.");
        }
        if (audio.length > MAX_BYTES || segundos < 1 || segundos > MAX_SEGUNDOS) {
            throw new UnprocessableException("El dictado puede durar hasta tres minutos.");
        }
        if (!presupuesto.dictadoDisponible()) {
            throw new ServiceUnavailableException(
                    "El dictado no está disponible en este momento. Puedes escribir tus notas en la caja.");
        }
        return transcriptor.transcribir(profesor.getId(), audio, tipo, segundos)
                .orElseThrow(() -> new UnprocessableException(
                        "No alcanzamos a entender el audio. Intenta de nuevo o escríbelo."));
    }
}
