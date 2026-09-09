package co.orion.lifecycle.application;

import java.util.UUID;

import co.orion.scheduling.domain.AbsenceKind;

/**
 * Quedó registrada una falta del profesor. La escalera de sanciones vive en {@code reputation} y se
 * entera por aquí, igual que se entera de un reclamo resuelto: por evento, nunca por llamada.
 */
public record ProfessorAbsenceRecorded(UUID professorId, UUID bookingId, AbsenceKind kind) {
}
