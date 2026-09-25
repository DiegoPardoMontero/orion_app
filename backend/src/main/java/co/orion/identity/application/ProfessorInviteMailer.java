package co.orion.identity.application;

/** Envía el correo de invitación a un profesor. Interfaz para poder capturar el enlace en tests. */
public interface ProfessorInviteMailer {

    /**
     * @param professorName con qué nombre se saluda al profe, o {@code null}
     * @param inviterName   quién lo invita, como lo ve el profe
     */
    void sendInvite(String toEmail, String professorName, String inviterName, String inviteLink);
}
