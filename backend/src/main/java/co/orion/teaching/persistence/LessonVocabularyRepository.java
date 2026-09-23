package co.orion.teaching.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.teaching.domain.LessonVocabulary;

public interface LessonVocabularyRepository extends JpaRepository<LessonVocabulary, UUID> {

    List<LessonVocabulary> findByLessonNoteIdOrderByDisplayOrderAsc(UUID lessonNoteId);

    List<LessonVocabulary> findByLessonNoteIdInOrderByDisplayOrderAsc(Collection<UUID> lessonNoteIds);

    @Modifying
    @Query("delete from LessonVocabulary v where v.lessonNoteId = :nota")
    void borrarDe(@Param("nota") UUID nota);
}
