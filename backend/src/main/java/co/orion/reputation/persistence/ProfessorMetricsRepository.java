package co.orion.reputation.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.reputation.domain.ProfessorMetrics;

public interface ProfessorMetricsRepository extends JpaRepository<ProfessorMetrics, UUID> {

    /** Las métricas de una página de profesores, en una sola consulta (buscador/directorio). */
    List<ProfessorMetrics> findByProfessorIdIn(Collection<UUID> professorIds);
}
