package co.orion.identity.application;

import co.orion.identity.domain.SignupIntent;

/**
 * Envía el enlace de verificación. Interfaz, como {@link PasswordResetMailer}, para que un test
 * pueda capturar el enlace —el token en claro solo existe en el correo— sin levantar SMTP.
 */
public interface EmailVerificationMailer {

    /**
     * @param intent por qué puerta entró la cuenta. Decide el texto: a quien vino a enseñar no se
     *               le pide confirmar el correo «para reservar clases».
     */
    void sendVerificationLink(String toEmail, String fullName, String verificationLink,
                              SignupIntent intent);
}
