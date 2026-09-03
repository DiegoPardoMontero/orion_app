package co.orion.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.reputation.application.SanctionService;
import co.orion.shared.error.ForbiddenException;

/**
 * El gate de visibilidad de Orión: nadie enseña sin una postulación APPROVED, y nadie recibe
 * reservas nuevas mientras una sanción activa se lo impida. Es la ÚNICA regla de "puede enseñar" —
 * la comparten el buscador, el detalle público, la publicación del perfil, la reserva y los cupos.
 * Los profesores por invitación nacen con una postulación APPROVED, así que pasan por la misma puerta.
 *
 * Aquí es donde la sanción se convierte en consecuencia. Vive en identity y no en scheduling por una
 * razón estructural: reputation lee las reservas, así que scheduling no puede leer reputation sin
 * cerrar un ciclo. identity ya dependía de reputation, y todo el mundo pasa por este gate.
 */
@Service
public class ProfessorAccessService {

    private final TeacherApplicationRepository applications;
    private final SanctionService sanctions;

    public ProfessorAccessService(TeacherApplicationRepository applications,
                                  SanctionService sanctions) {
        this.applications = applications;
        this.sanctions = sanctions;
    }

    @Transactional(readOnly = true)
    public boolean isApproved(UUID professorId) {
        return applications.existsByUserIdAndStatus(professorId, ApplicationStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public void assertCanTeach(UUID professorId) {
        if (!isApproved(professorId)) {
            throw new ForbiddenException("Tu perfil aún no está aprobado para enseñar.");
        }
        // Las clases YA confirmadas se respetan siempre: esto solo cierra la puerta a las nuevas.
        if (!sanctions.acceptsNewBookings(professorId)) {
            throw new ForbiddenException(
                    "Este profesor no está recibiendo reservas nuevas en este momento.");
        }
    }
}
