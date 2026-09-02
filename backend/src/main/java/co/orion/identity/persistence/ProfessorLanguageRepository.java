package co.orion.identity.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageId;

public interface ProfessorLanguageRepository extends JpaRepository<ProfessorLanguage, ProfessorLanguageId> {

    List<ProfessorLanguage> findByProfessorId(UUID professorId);

    List<ProfessorLanguage> findByProfessorIdIn(Collection<UUID> professorIds);

    /** Bulk DELETE inmediato (no retrieve-then-remove): así el borrado llega a la BD antes de los
     * re-inserts de la misma edición y no choca por PK repetida. */
    @Modifying
    @Query("delete from ProfessorLanguage pl where pl.professorId = :professorId")
    void deleteByProfessorId(@Param("professorId") UUID professorId);
}
