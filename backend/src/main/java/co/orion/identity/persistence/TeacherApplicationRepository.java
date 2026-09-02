package co.orion.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.TeacherApplication;

public interface TeacherApplicationRepository extends JpaRepository<TeacherApplication, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, ApplicationStatus status);

    /** La postulación "viva" del usuario (la que no está en un estado terminal). */
    Optional<TeacherApplication> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, List<ApplicationStatus> statuses);

    Optional<TeacherApplication> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<TeacherApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}
