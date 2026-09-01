package co.orion.identity.application;

/**
 * Envía el enlace de recuperación. Interfaz para que los tests puedan capturar el enlace (el token
 * en claro solo existe en el correo) sin levantar SMTP.
 */
public interface PasswordResetMailer {

    void sendResetLink(String toEmail, String fullName, String resetLink);
}
