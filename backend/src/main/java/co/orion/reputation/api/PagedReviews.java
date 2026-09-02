package co.orion.reputation.api;

import java.util.List;

/** Página de reseñas con la forma explícita que consume el frontend (sin exponer el Page de Spring). */
public record PagedReviews(
        List<PublicReviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
