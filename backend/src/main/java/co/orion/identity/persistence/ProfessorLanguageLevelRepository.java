package co.orion.identity.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorLanguageLevelId;

public interface ProfessorLanguageLevelRepository
        extends JpaRepository<ProfessorLanguageLevel, ProfessorLanguageLevelId> {

    List<ProfessorLanguageLevel> findByProfessorId(UUID professorId);

    List<ProfessorLanguageLevel> findByProfessorIdIn(Collection<UUID> professorIds);

    @Modifying
    @Query("delete from ProfessorLanguageLevel l where l.professorId = :professorId")
    void deleteByProfessorId(@Param("professorId") UUID professorId);
}
