package co.orion.identity.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.TeacherApplicationEvent;

public interface TeacherApplicationEventRepository extends JpaRepository<TeacherApplicationEvent, UUID> {

    List<TeacherApplicationEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
