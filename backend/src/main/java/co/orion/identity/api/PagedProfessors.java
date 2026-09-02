package co.orion.identity.api;

import java.util.List;

/** Página de resultados con la forma explícita que consume el frontend (sin exponer el Page de Spring). */
public record PagedProfessors(
        List<ProfessorCard> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
