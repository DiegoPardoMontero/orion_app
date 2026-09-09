package co.orion.identity.application;

/**
 * Envía el enlace de verificación. Interfaz, como {@link PasswordResetMailer}, para que un test
 * pueda capturar el enlace —el token en claro solo existe en el correo— sin levantar SMTP.
 */
public interface EmailVerificationMailer {

    void sendVerificationLink(String toEmail, String fullName, String verificationLink);
}
