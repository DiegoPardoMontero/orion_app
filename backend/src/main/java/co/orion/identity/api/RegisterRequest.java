package co.orion.identity.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta que hace la propia persona desde la pantalla de registro. No lleva rol: el auto-registro
 * siempre nace STUDENT (crear profesores o admins es decisión de negocio, no de un formulario
 * público). El WhatsApp es opcional; es el canal por el que luego coordinará con su profesor.
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
        String password,
        @Size(max = 20) String whatsappPhone,
        boolean wantsToTeach,
        @AssertTrue(message = "Orión está disponible solo para mayores de 18 años.")
        boolean adult,
        @AssertTrue(message = "Debes aceptar los Términos y condiciones para crear tu cuenta.")
        boolean acceptsTerms,
        @AssertTrue(message = "Necesitamos tu autorización para tratar tus datos personales.")
        boolean acceptsDataPolicy) {
}
