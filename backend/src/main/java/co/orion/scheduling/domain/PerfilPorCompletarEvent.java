package co.orion.scheduling.domain;

import java.util.List;
import java.util.UUID;

/** Toca recordarle al profesor lo que le falta para recibir estudiantes. Paso 1, 2 o 3. */
public record PerfilPorCompletarEvent(UUID professorId, int paso, List<String> faltan) {
}
