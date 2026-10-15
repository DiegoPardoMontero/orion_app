package co.orion.identity.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.TeacherDocumentService;
import co.orion.shared.security.OrionUserDetails;

/**
 * El profe (o aspirante) abre lo que subió: el previsualizador de la postulación y de «Clases con
 * adolescentes» (pedido de Pardo del 15/10/2026). Solo lo suyo; con otro id responde 404.
 */
@RestController
public class MyTeacherDocumentsController {

    private final TeacherDocumentService documents;

    public MyTeacherDocumentsController(TeacherDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/api/v1/me/teacher-documents/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal OrionUserDetails principal,
                                          @PathVariable UUID id,
                                          @RequestParam(defaultValue = "false") boolean descargar) {
        return DocumentoEnLinea.respuesta(documents.contentForOwner(principal.user().getId(), id), descargar);
    }
}
