package co.orion.assessment.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un turno, tal como lo empuja el cliente de voz.
 *
 * <p>Solo viaja lo que el navegador es el único que puede saber: cuánto tardó la persona en abrir
 * la boca y cuánto habló. <strong>Las señales no se aceptan del cliente</strong> — las deduce el
 * servidor del texto. Un puntaje construido sobre números que manda el navegador no es
 * reproducible, y cualquiera podría regalarse un cien.
 */
public record AddTurnRequest(
        // Una conversación de diagnóstico no llega a cien turnos; el tope evita que un cliente
        // escriba miles de filas en una sola evaluación.
        @NotNull @Min(0) @Max(400) Integer turnIndex,
        @NotBlank @Size(max = 10) String speaker,
        @Size(max = 2000) String transcript,
        @Min(0) Integer latencyMs,
        @Min(0) Integer durationMs) {
}
