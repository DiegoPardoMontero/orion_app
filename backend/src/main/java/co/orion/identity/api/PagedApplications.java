package co.orion.identity.api;

import java.util.List;

/** Página de la bandeja de postulaciones. */
public record PagedApplications(
        List<AdminApplicationSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
