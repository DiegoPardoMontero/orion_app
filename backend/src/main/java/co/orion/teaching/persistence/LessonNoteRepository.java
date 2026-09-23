package co.orion.teaching.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.teaching.domain.LessonNote;
import co.orion.teaching.domain.LessonNoteStatus;

public interface LessonNoteRepository extends JpaRepository<LessonNote, UUID> {

    Optional<LessonNote> findByBookingId(UUID bookingId);

    List<LessonNote> findByProfessorIdOrderByCreatedAtDesc(UUID professorId);

    List<LessonNote> findByProfessorIdAndStatusOrderByCreatedAtDesc(UUID professorId, LessonNoteStatus status);

    List<LessonNote> findByStudentIdAndStatusOrderByPublishedAtDesc(UUID studentId, LessonNoteStatus status,
                                                                     Pageable page);

    List<LessonNote> findByStudentIdAndStatus(UUID studentId, LessonNoteStatus status);
}
