package co.orion.identity.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.io.IOException;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.MinorsClearanceService;
import co.orion.identity.application.TeacherDocumentService;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;

/**
 * «Clases con adolescentes» en Mi perfil del profe (Bloque 12): cómo va su habilitación, aceptar el
 * código de conducta y subir o quitar los dos certificados. Antes se subían por el endpoint de la
 * postulación, que un profe aprobado ya no puede editar: los primeros profes reales vieron un 409
 * (15/10/2026). Desde aquí no pasa por la postulación.
 */
@RestController
@RequestMapping("/api/v1/me/profile/minors")
public class MyMinorsClearanceController {

    private final MinorsClearanceService clearance;
    private final TeacherDocumentService documents;

    public MyMinorsClearanceController(MinorsClearanceService clearance, TeacherDocumentService documents) {
        this.clearance = clearance;
        this.documents = documents;
    }

    @PostMapping("/documents")
    public MinorsClearanceView uploadDocument(@AuthenticationPrincipal OrionUserDetails principal,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam("docType") String docType) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleViolationException("No se pudo leer el archivo");
        }
        documents.uploadMinorsDocument(principal.user().getId(), bytes, file.getContentType(),
                file.getOriginalFilename(), docType);
        return clearance.view(principal.user().getId());
    }

    @DeleteMapping("/documents/{id}")
    public MinorsClearanceView deleteDocument(@AuthenticationPrincipal OrionUserDetails principal,
                                              @PathVariable UUID id) {
        documents.deleteMinorsDocument(principal.user().getId(), id);
        return clearance.view(principal.user().getId());
    }

    @GetMapping
    public MinorsClearanceView mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return clearance.view(principal.user().getId());
    }

    @PostMapping("/conduct")
    public MinorsClearanceView acceptConduct(@AuthenticationPrincipal OrionUserDetails principal,
                                             HttpServletRequest http) {
        return clearance.acceptConduct(principal.user(), http.getRemoteAddr(), http.getHeader("User-Agent"));
    }
}
