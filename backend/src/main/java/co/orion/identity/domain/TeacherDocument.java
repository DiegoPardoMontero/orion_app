package co.orion.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un documento privado de un aspirante (CV, título...). Guardamos el {@code storage_key} (el
 * public_id de Cloudinary), NUNCA una URL: los documentos son {@code authenticated} y solo se
 * sirven por una URL firmada de corta vida que genera el admin.
 */
@Entity
@Table(name = "teacher_documents")
@EntityListeners(AuditingEntityListener.class)
public class TeacherDocument {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    private DocumentType docType;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @CreatedDate
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected TeacherDocument() {
        // exigido por JPA
    }

    public TeacherDocument(UUID userId, UUID applicationId, DocumentType docType,
                           String fileName, String storageKey, String contentType, int sizeBytes) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.docType = docType;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
