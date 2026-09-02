package co.orion.identity.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.AdminAuditService;
import co.orion.identity.application.TeacherDocumentService;
import co.orion.shared.security.OrionUserDetails;

/**
 * Acceso del admin a los documentos privados de un aspirante. Cada consulta de URL firmada queda en
 * la bitácora: quién miró qué documento y cuándo. Solo ADMIN (la ruta /admin/** ya lo exige).
 */
@RestController
@RequestMapping("/api/v1/admin/teachers")
public class AdminTeacherDocumentsController {

    private final TeacherDocumentService documents;
    private final AdminAuditService audit;

    public AdminTeacherDocumentsController(TeacherDocumentService documents, AdminAuditService audit) {
        this.documents = documents;
        this.audit = audit;
    }

    @GetMapping("/{userId}/documents/{docId}/url")
    public Map<String, String> documentUrl(@AuthenticationPrincipal OrionUserDetails principal,
                                           @PathVariable UUID userId,
                                           @PathVariable UUID docId) {
        TeacherDocumentService.SignedDocument signed = documents.signedUrlForAdmin(userId, docId);
        audit.record(principal.user().getId(), "VIEW_DOCUMENT", "teacher_document", docId,
                "{\"owner\":\"" + userId + "\"}");
        return Map.of("url", signed.url());
    }
}
