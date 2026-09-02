package co.orion.reputation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * El cuerpo para reseñar una reserva. rating 1..5 (validado también por la CHECK de la BD, que es el
 * árbitro final). comment opcional; más de 1000 caracteres es un 400 de validación, no un truncado.
 */
public record CreateReviewRequest(
        @Min(value = 1, message = "El rating mínimo es 1")
        @Max(value = 5, message = "El rating máximo es 5")
        short rating,

        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres")
        String comment) {
}
