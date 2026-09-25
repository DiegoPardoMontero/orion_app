package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La invitación que el admin le manda a un profe (V71).
 *
 * @param professorName con qué nombre se le saluda en la pantalla de invitación; opcional
 * @param founder       si trae el beneficio de profe fundador; si no se manda, sí
 * @param inviterTitle  el cargo de quien invita («directora académica»); queda en su cuenta
 */
public record InviteProfessorRequest(@NotBlank @Email @Size(max = 254) String email,
                                     @Size(max = 80) String professorName,
                                     Boolean founder,
                                     @Size(max = 80) String inviterTitle) {
}
