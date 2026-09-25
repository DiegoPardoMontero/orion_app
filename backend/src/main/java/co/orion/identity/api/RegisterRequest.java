package co.orion.identity.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import co.orion.shared.WhatsappValido;
import co.orion.shared.security.CabeEnBcrypt;

/**
 * Alta que hace la propia persona desde la pantalla de registro. No lleva rol: el auto-registro
 * siempre nace STUDENT (crear profesores o admins es decisión de negocio, no de un formulario
 * público). El WhatsApp es obligatorio (Pardo, 25/09/2026): por ahí le avisamos de sus clases, y
 * al aspirante lo ubica el equipo mientras revisa su postulación.
 *
 * <p>{@code wantsToTeach} es la intención, no el rol: dice por cuál de las dos puertas entró. Quien
 * entra por «Postúlate para dar clases» no es un estudiante que además postula — es un aspirante, y
 * hasta que su postulación se apruebe no tiene nada que hacer en la experiencia del estudiante.
 *
 * <p>{@code adult}, {@code acceptsTerms} y {@code acceptsDataPolicy} van por separado y las tres
 * son obligatorias. Empaquetarlas en una sola casilla viciaría la autorización de datos, que el
 * Decreto 1377 de 2013 exige previa, expresa e informada — y por tanto específica.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        @CabeEnBcrypt String password,
        @NotBlank(message = "Tu WhatsApp es obligatorio.") @Size(max = 20) @WhatsappValido String whatsappPhone,
        boolean wantsToTeach,
        @AssertTrue(message = "Orión está disponible solo para mayores de 18 años.")
        boolean adult,
        @AssertTrue(message = "Debes aceptar los Términos y condiciones para crear tu cuenta.")
        boolean acceptsTerms,
        @AssertTrue(message = "Necesitamos tu autorización para tratar tus datos personales.")
        boolean acceptsDataPolicy,
        /** El token de una invitación de profesor, si llegó por ella (V71). */
        @Size(max = 100) String inviteToken) {

    /** Sin invitación: el alta de siempre. */
    public RegisterRequest(String fullName, String email, String password, String whatsappPhone,
                           boolean wantsToTeach, boolean adult, boolean acceptsTerms, boolean acceptsDataPolicy) {
        this(fullName, email, password, whatsappPhone, wantsToTeach, adult, acceptsTerms, acceptsDataPolicy, null);
    }
}
