package co.orion.identity.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.identity.domain.TeacherDocument;

/** Metadatos de un documento (nunca su URL: los documentos son privados). */
public record DocumentView(
        UUID id,
        String docType,
        String fileName,
        String contentType,
        int sizeBytes,
        Instant uploadedAt) {

    public static DocumentView of(TeacherDocument d) {
        return new DocumentView(d.getId(), d.getDocType().name(), d.getFileName(),
                d.getContentType(), d.getSizeBytes(), d.getUploadedAt());
    }
}
