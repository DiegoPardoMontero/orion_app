package co.orion.lifecycle.application;

import java.util.UUID;

/**
 * Se cerró un reclamo. {@code absenceRecorded} dice si quedó una ausencia del profesor — que es lo
 * que dispara la evaluación de sanciones del Bloque 6.
 */
public record DisputeResolved(UUID disputeId, UUID professorId, boolean absenceRecorded) {
}
