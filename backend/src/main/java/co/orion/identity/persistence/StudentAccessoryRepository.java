package co.orion.identity.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.StudentAccessory;
import co.orion.identity.domain.StudentAccessoryId;

public interface StudentAccessoryRepository extends JpaRepository<StudentAccessory, StudentAccessoryId> {

    List<StudentAccessory> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
