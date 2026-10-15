package co.orion.identity.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.TeacherDocument;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

/**
 * Documentos de una postulación. Valida por el content-type REAL del archivo (no la extensión),
 * tamaño y un tope de 6 documentos por aspirante. Guarda solo el storage_key; la lectura es siempre
 * por una URL firmada que genera el admin.
 */
@Service
public class TeacherDocumentService {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_DOCUMENTS = 6;
    /**
     * Los certificados de «Clases con adolescentes» (Bloque 12) no entran en el tope de la postulación
     * —un profe aprobado con sus seis documentos tiene que poder subirlos— y tienen el suyo, por tipo.
     */
    public static final List<DocumentType> MINORS_TYPES = List.of(DocumentType.REGISTRY_CHECK, DocumentType.BACKGROUND_CHECK);
    private static final int MAX_PER_MINORS_TYPE = 3;
    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(5);
    private static final List<ApplicationStatus> OPEN = List.of(
            ApplicationStatus.DRAFT, ApplicationStatus.PENDING_REVIEW,
            ApplicationStatus.UNDER_REVIEW, ApplicationStatus.CHANGES_REQUESTED);

    private final TeacherDocumentRepository documents;
    private final TeacherApplicationRepository applications;
    private final DocumentStorage storage;

    public TeacherDocumentService(TeacherDocumentRepository documents,
                                  TeacherApplicationRepository applications,
                                  DocumentStorage storage) {
        this.documents = documents;
        this.applications = applications;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<TeacherDocument> listOwn(UUID userId) {
        return documents.findByUserId(userId);
    }

    @Transactional
    public TeacherDocument upload(UUID userId, byte[] bytes, String contentType, String fileName, String docTypeName) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleViolationException("El archivo llegó vacío");
        }
        if (bytes.length > MAX_BYTES) {
            throw new UnprocessableException("El documento no puede superar 10 MB");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!type.equals("application/pdf") && !type.startsWith("image/")) {
            throw new BusinessRuleViolationException("El documento debe ser un PDF o una imagen");
        }
        DocumentType docType = parseDocType(docTypeName);
        if (MINORS_TYPES.contains(docType)) {
            if (documents.countByUserIdAndDocType(userId, docType) >= MAX_PER_MINORS_TYPE) {
                throw new BusinessRuleViolationException(
                        "Ya subiste " + MAX_PER_MINORS_TYPE + " archivos de este certificado: quita alguno antes de subir otro");
            }
        } else if (documents.countByUserIdAndDocTypeNotIn(userId, MINORS_TYPES) >= MAX_DOCUMENTS) {
            throw new BusinessRuleViolationException(
                    "No puedes subir más de " + MAX_DOCUMENTS + " documentos");
        }

        UUID applicationId = applications
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, OPEN)
                .map(TeacherApplication::getId)
                .orElse(null);

        String storageKey = storage.upload(bytes, type, userId, fileName);
        TeacherDocument document = new TeacherDocument(
                userId, applicationId, docType, safeName(fileName), storageKey, type, bytes.length);
        return documents.save(document);
    }

    /**
     * Un certificado de «Clases con adolescentes», desde Mi perfil: no pasa por la postulación, que
     * un profe aprobado ya no puede editar (el 409 que vieron los primeros profes, 15/10/2026).
     */
    @Transactional
    public TeacherDocument uploadMinorsDocument(UUID userId, byte[] bytes, String contentType, String fileName,
                                                String docTypeName) {
        DocumentType docType = parseDocType(docTypeName);
        if (!MINORS_TYPES.contains(docType)) {
            throw new BusinessRuleViolationException("docType debe ser REGISTRY_CHECK o BACKGROUND_CHECK");
        }
        return upload(userId, bytes, contentType, fileName, docType.name());
    }

    /** Solo lo suyo y solo de esos dos tipos; lo demás es «no encontrado». */
    @Transactional
    public void deleteMinorsDocument(UUID userId, UUID documentId) {
        TeacherDocument document = documents.findByIdAndUserId(documentId, userId)
                .filter(d -> MINORS_TYPES.contains(d.getDocType()))
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public List<TeacherDocument> listMinors(UUID userId) {
        return documents.findByUserIdAndDocTypeIn(userId, MINORS_TYPES);
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        // findByIdAndUserId: un documento ajeno responde 404 (no revelamos que existe).
        TeacherDocument document = documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        documents.delete(document);
    }

    /** URL firmada para que el admin vea un documento. Devuelve también el propio documento (para auditar). */
    @Transactional(readOnly = true)
    public SignedDocument signedUrlForAdmin(UUID ownerId, UUID documentId) {
        TeacherDocument document = documents.findByIdAndUserId(documentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        String url = storage.signedUrl(document.getStorageKey(), document.getContentType(), SIGNED_URL_TTL);
        return new SignedDocument(document, url);
    }

    public record SignedDocument(TeacherDocument document, String url) {
    }

    /** Un documento con sus bytes, para servirlo en línea (previsualizador) o como descarga. */
    public record Contenido(TeacherDocument document, byte[] bytes) {
    }

    /** El dueño abre lo suyo: un id que no es suyo es «no encontrado», no «prohibido». */
    public Contenido contentForOwner(UUID userId, UUID documentId) {
        TeacherDocument document = documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));
        return new Contenido(document, storage.download(document.getStorageKey(), document.getContentType()));
    }

    public Contenido contentForAdmin(UUID ownerId, UUID documentId) {
        return contentForOwner(ownerId, documentId);
    }

    private DocumentType parseDocType(String name) {
        try {
            return DocumentType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleViolationException(
                    "docType debe ser CV, TEACHING_CERTIFICATE, UNIVERSITY_DEGREE, "
                            + "LANGUAGE_CERTIFICATION, OTHER, REGISTRY_CHECK o BACKGROUND_CHECK");
        }
    }

    private String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "documento";
        }
        return fileName.length() > 200 ? fileName.substring(0, 200) : fileName;
    }
}
