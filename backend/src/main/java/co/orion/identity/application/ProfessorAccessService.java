package co.orion.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.shared.error.ForbiddenException;

/**
 * El gate de visibilidad de Orión: nadie enseña sin una postulación APPROVED. Es la ÚNICA regla de
 * "puede enseñar" — la comparten el buscador, el detalle público, la publicación del perfil, la
 * reserva y los cupos. Los profesores por invitación nacen con una postulación APPROVED, así que
 * pasan por la misma puerta.
 */
@Service
public class ProfessorAccessService {

    private final TeacherApplicationRepository applications;

    public ProfessorAccessService(TeacherApplicationRepository applications) {
        this.applications = applications;
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
    }
}
