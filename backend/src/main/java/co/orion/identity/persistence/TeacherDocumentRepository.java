package co.orion.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.TeacherDocument;

public interface TeacherDocumentRepository extends JpaRepository<TeacherDocument, UUID> {

    List<TeacherDocument> findByUserId(UUID userId);

    Optional<TeacherDocument> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndDocType(UUID userId, DocumentType docType);

    long countByUserId(UUID userId);
}
