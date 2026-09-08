package co.orion.identity.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Filtros del buscador de profesores. Todos opcionales y combinables. Un campo nulo/vacío no filtra.
 */
public record ProfessorSearchCriteria(
        String language,
        List<String> levels,
        List<String> goals,
        Long minPrice,
        Long maxPrice,
        Boolean certified,
        Boolean nativeOnly,
        /** Días de la semana pedidos, en hora de Bogotá. Vacío o nulo: cualquiera. */
        Set<DayOfWeek> days,
        /** Franja horaria pedida. Nulos: sin límite por ese lado. */
        LocalTime from,
        LocalTime to,
        /**
         * Profesores que una sanción activa saca del buscador. Llegan resueltos desde el servicio:
         * meter esa consulta dentro de la Specification ataría el buscador al esquema de reputation.
         */
        List<UUID> hiddenProfessorIds,
        /**
         * Profesores que cumplen el filtro de disponibilidad, ya resueltos por
         * {@link ProfessorAvailabilityLookup}.
         *
         * <p><strong>{@code null} y lista vacía NO son lo mismo.</strong> Nulo significa «no se
         * pidió filtrar por disponibilidad»; vacía significa «se pidió y no lo cumple nadie», y
         * entonces el resultado tiene que ser vacío. Confundir las dos cosas devolvería el
         * catálogo entero justo cuando alguien acaba de pedir algo muy concreto.
         */
        List<UUID> availableProfessorIds) {

    /** Sin filtros de disponibilidad ni sancionados: el punto de partida del controlador. */
    public static ProfessorSearchCriteria of(String language, List<String> levels, List<String> goals,
                                             Long minPrice, Long maxPrice, Boolean certified,
                                             Boolean nativeOnly, Set<DayOfWeek> days,
                                             LocalTime from, LocalTime to) {
        return new ProfessorSearchCriteria(language, levels, goals, minPrice, maxPrice, certified,
                nativeOnly, days, from, to, List.of(), null);
    }

    /** Si alguien pidió filtrar por día u hora. */
    public boolean filtraPorDisponibilidad() {
        return (days != null && !days.isEmpty()) || from != null || to != null;
    }

    /** Con los ids de sancionados ya resueltos. */
    public ProfessorSearchCriteria hiding(List<UUID> hidden) {
        return new ProfessorSearchCriteria(language, levels, goals, minPrice, maxPrice, certified,
                nativeOnly, days, from, to, hidden == null ? List.of() : hidden,
                availableProfessorIds);
    }

    /** Con los ids que cumplen la disponibilidad ya resueltos. */
    public ProfessorSearchCriteria availableOnly(List<UUID> available) {
        return new ProfessorSearchCriteria(language, levels, goals, minPrice, maxPrice, certified,
                nativeOnly, days, from, to, hiddenProfessorIds, available);
    }
}
