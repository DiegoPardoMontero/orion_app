package co.orion.identity.api;

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
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,
        @Size(max = 20) String whatsappPhone,
        boolean wantsToTeach) {
}
