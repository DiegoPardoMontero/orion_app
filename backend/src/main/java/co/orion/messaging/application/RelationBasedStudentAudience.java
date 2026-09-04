package co.orion.messaging.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.StudentAudience;
import co.orion.messaging.persistence.ConversationRepository;
import co.orion.scheduling.persistence.BookingRepository;

/**
 * Implementa el puerto {@link StudentAudience}. Vive aquí y no en {@code identity} porque la
 * respuesta necesita reservas y conversaciones a la vez, y este es el módulo que ya ve las dos.
 *
 * <p>«Público para los profesores» no puede significar <em>todos</em> los profesores: eso
 * convertiría la plataforma en un directorio navegable de personas. Hace falta una relación real.
 */
@Component
public class RelationBasedStudentAudience implements StudentAudience {

    private final BookingRepository bookings;
    private final ConversationRepository conversations;

    public RelationBasedStudentAudience(BookingRepository bookings,
                                        ConversationRepository conversations) {
        this.bookings = bookings;
        this.conversations = conversations;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean professorHasRelationWith(UUID professorId, UUID studentId) {
        // Cualquier estado, canceladas incluidas: si compartieron una clase que se canceló, la
        // relación existió igual.
        return bookings.existsByProfessorIdAndStudentId(professorId, studentId)
                || conversations.findByStudentIdAndProfessorId(studentId, professorId).isPresent();
    }
}
