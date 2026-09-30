package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * La invitación que el admin le manda a un profe (V71).
 *
 * @param email         opcional desde la V80: sin él, el enlace se manda por WhatsApp
 * @param professorName con qué nombre se le saluda en la pantalla de invitación; obligatorio si no hay
 *                      correo, para saber a quién se le mandó cada enlace
 * @param founder       si trae el beneficio de profe fundador; si no se manda, sí
 * @param inviterTitle  ya no se usa: la invitación la firma Orión y no sale el cargo de nadie. Se sigue
 *                      aceptando para no romper a un cliente que lo mande, y se ignora.
 */
public record InviteProfessorRequest(@Email @Size(max = 254) String email,
                                     @Size(max = 80) String professorName,
                                     Boolean founder,
                                     @Size(max = 80) String inviterTitle) {
}
