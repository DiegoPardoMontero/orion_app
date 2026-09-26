package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La invitación que el admin le manda a un profe (V71).
 *
 * @param professorName con qué nombre se le saluda en la pantalla de invitación; opcional
 * @param founder       si trae el beneficio de profe fundador; si no se manda, sí
 * @param inviterTitle  ya no se usa: la invitación la firma Orión y no sale el cargo de nadie. Se sigue
 *                      aceptando para no romper a un cliente que lo mande, y se ignora.
 */
public record InviteProfessorRequest(@NotBlank @Email @Size(max = 254) String email,
                                     @Size(max = 80) String professorName,
                                     Boolean founder,
                                     @Size(max = 80) String inviterTitle) {
}
