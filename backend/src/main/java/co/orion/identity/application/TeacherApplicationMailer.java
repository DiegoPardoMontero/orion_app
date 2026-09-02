package co.orion.identity.application;

/** Avisa al aspirante de la decisión sobre su postulación. Interfaz para capturarlo en tests. */
public interface TeacherApplicationMailer {

    void sendApproved(String toEmail);

    void sendChangesRequested(String toEmail, String note);

    void sendRejected(String toEmail, String note);
}
