package co.orion.identity.application;

import java.util.List;

/**
 * Filtros del buscador de profesores. Todos opcionales y combinables. Un campo nulo/vacío no filtra.
 * availableDay/availableTime del brief quedan para una iteración siguiente (filtro contra
 * availability_rules): se documenta en el OpenAPI para no fingir que ya existen.
 */
public record ProfessorSearchCriteria(
        String language,
        List<String> levels,
        List<String> goals,
        Long minPrice,
        Long maxPrice,
        Boolean certified,
        Boolean nativeOnly,
        /**
         * Profesores que una sanción activa saca del buscador. Llegan resueltos desde el servicio:
         * meter esa consulta dentro de la Specification ataría el buscador al esquema de reputation.
         */
        List<java.util.UUID> hiddenProfessorIds) {

    /** Con los ids de sancionados ya resueltos. */
    public ProfessorSearchCriteria hiding(List<java.util.UUID> hidden) {
        return new ProfessorSearchCriteria(language, levels, goals, minPrice, maxPrice,
                certified, nativeOnly, hidden == null ? List.of() : hidden);
    }
}
