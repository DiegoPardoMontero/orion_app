package co.orion.identity.application;

/** Envía el correo de invitación a un profesor. Interfaz para poder capturar el enlace en tests. */
public interface ProfessorInviteMailer {

    /**
     * Sin quién invita: la invitación la firma Orión (Pardo, 26/09/2026), nunca el nombre ni el cargo
     * de la persona que la mandó desde administración.
     *
     * @param professorName con qué nombre se saluda al profe, o {@code null}
     */
    void sendInvite(String toEmail, String professorName, String inviteLink);
}
