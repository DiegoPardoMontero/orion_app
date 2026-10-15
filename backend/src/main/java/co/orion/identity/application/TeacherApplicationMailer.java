package co.orion.identity.application;

import java.util.List;
import java.util.UUID;

/**
 * Avisa al aspirante de la decisión sobre su postulación, y al equipo de que llegó una por revisar.
 * Interfaz para capturarlo en tests.
 */
public interface TeacherApplicationMailer {

    /** Al equipo (los admins activos): llegó una postulación, o volvió con los cambios pedidos. */
    void sendSubmittedToTeam(List<String> toEmails, String applicantName, UUID applicationId, boolean resubmitted);

    /** Un profe nuevo abrió su postulación (15/10/2026): el equipo lo sabe aunque todavía no envíe nada. */
    void sendStartedToTeam(List<String> toEmails, String applicantName, String applicantEmail);

    void sendApproved(String toEmail);

    void sendChangesRequested(String toEmail, String note);

    void sendRejected(String toEmail, String note);
}
