package co.orion.lifecycle.application;

import java.util.UUID;

/**
 * Se cerró un reclamo. {@code absenceRecorded} dice si quedó una ausencia del profesor — que es lo
 * que dispara la evaluación de sanciones del Bloque 6.
 */
/**
 * {@code favoursStudent} dice cómo se resolvió; {@code absenceRecorded}, si eso le dejó una falta al
 * profesor. Casi siempre coinciden. No coinciden en el ensayo del admin: se resuelve a favor del
 * estudiante y no cuenta como ausencia, porque no era trabajo del profesor (decisión de Pardo,
 * 14/10/2026).
 */
public record DisputeResolved(UUID disputeId, UUID professorId, boolean favoursStudent, boolean absenceRecorded) {
}
