package co.orion.identity.application;

/** Envía el correo de invitación a un profesor. Interfaz para poder capturar el enlace en tests. */
public interface ProfessorInviteMailer {

    void sendInvite(String toEmail, String inviteLink);
}
