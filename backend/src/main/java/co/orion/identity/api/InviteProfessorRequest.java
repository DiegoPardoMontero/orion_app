package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** El correo del profesor que el admin invita. */
public record InviteProfessorRequest(@NotBlank @Email String email) {
}
