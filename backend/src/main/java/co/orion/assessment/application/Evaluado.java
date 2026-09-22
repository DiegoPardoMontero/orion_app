package co.orion.assessment.application;

import java.util.UUID;

import co.orion.assessment.domain.AssessmentLead;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.identity.domain.User;

/**
 * Quién está haciendo el diagnóstico: una cuenta o un lead, y nunca los dos a la vez.
 *
 * <p>El servicio no necesita saber más que esto de la persona: a quién pertenece la evaluación,
 * cómo se llama (Meissa la saluda por su nombre de pila) y a quién se le carga el gasto de IA, que
 * para un lead es a nadie: {@code ai_usage_log.actor_id} apunta a {@code users}.
 */
public record Evaluado(UUID userId, UUID leadId, String nombreDePila) {

    public static Evaluado cuenta(User user) {
        return new Evaluado(user.getId(), null, primerNombre(user.getFullName()));
    }

    public static Evaluado lead(AssessmentLead lead) {
        return new Evaluado(null, lead.getId(), lead.getFirstName());
    }

    public boolean esLead() {
        return userId == null;
    }

    /** Para el registro de gasto: solo una cuenta tiene fila en {@code users}. */
    public UUID actorId() {
        return userId;
    }

    /**
     * Un diagnóstico reclamado ya es de la cuenta, y el lead deja de verlo: la llave del
     * dispositivo no puede seguir abriendo algo que ahora protege una contraseña.
     */
    public boolean esDuenoDe(ConfidenceAssessment evaluacion) {
        return esLead()
                ? leadId.equals(evaluacion.getLeadId()) && evaluacion.getUserId() == null
                : userId.equals(evaluacion.getUserId());
    }

    private static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        int espacio = nombre.trim().indexOf(' ');
        return espacio < 0 ? nombre.trim() : nombre.trim().substring(0, espacio);
    }
}
