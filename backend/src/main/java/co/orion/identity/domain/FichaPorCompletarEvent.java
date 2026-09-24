package co.orion.identity.domain;

import java.util.List;
import java.util.UUID;

/**
 * Un recordatorio de completar la ficha (24/09/2026). Los pasos 1 y 3 van a la campana —los pinta
 * messaging, que es donde vive—; el 2 es el correo, que sale desde identity.
 *
 * @param faltan lo que le falta, en palabras («tu foto», «tu nivel»…)
 */
public record FichaPorCompletarEvent(UUID studentId, int paso, List<String> faltan) {
}
