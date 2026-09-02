package co.orion.identity.api;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.orion.identity.application.ProfessorProfileService;
import co.orion.identity.application.TeacherApplicationService;
import co.orion.identity.application.TeacherDocumentService;
import co.orion.identity.domain.TeacherDocument;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * El wizard de postulación del aspirante. El perfil (titular, bio, idiomas, objetivos) se guarda en
 * el mismo modelo del profesor; la postulación solo lleva el estado y el progreso.
 */
@RestController
public class TeacherApplicationController {

    private final TeacherApplicationService applications;
    private final TeacherDocumentService documents;
    private final ProfessorProfileService profiles;

    public TeacherApplicationController(TeacherApplicationService applications,
                                        TeacherDocumentService documents,
                                        ProfessorProfileService profiles) {
        this.applications = applications;
        this.documents = documents;
        this.profiles = profiles;
    }

    /** Crea (o devuelve) el borrador de postulación del usuario actual. */
    @PostMapping("/api/v1/teacher-applications")
    public TeacherApplicationView createOrGet(@AuthenticationPrincipal OrionUserDetails principal) {
        return applications.getOrCreateDraft(principal.user().getId());
    }

    @GetMapping("/api/v1/me/teacher-application")
    public TeacherApplicationView mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return applications.getMine(principal.user().getId());
    }

    /** Guarda el avance del perfil (sin publicar) y devuelve el estado de la postulación. */
    @PutMapping("/api/v1/me/teacher-application")
    public TeacherApplicationView save(@AuthenticationPrincipal OrionUserDetails principal,
                                       @Valid @RequestBody UpdateProfileRequest body) {
        UUID userId = principal.user().getId();
        profiles.saveApplicationProfile(userId, body);
        return applications.getOrCreateDraft(userId);
    }

    @PostMapping("/api/v1/me/teacher-application/documents")
    public DocumentView uploadDocument(@AuthenticationPrincipal OrionUserDetails principal,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam("docType") String docType) {
        TeacherDocument saved = documents.upload(
                principal.user().getId(), readBytes(file), file.getContentType(),
                file.getOriginalFilename(), docType);
        return DocumentView.of(saved);
    }

    @DeleteMapping("/api/v1/me/teacher-application/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@AuthenticationPrincipal OrionUserDetails principal,
                               @PathVariable UUID id) {
        documents.delete(principal.user().getId(), id);
    }

    @PostMapping("/api/v1/me/teacher-application/submit")
    public TeacherApplicationView submit(@AuthenticationPrincipal OrionUserDetails principal) {
        return applications.submit(principal.user().getId());
    }

    @PostMapping("/api/v1/me/agreements/{code}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptAgreement(@AuthenticationPrincipal OrionUserDetails principal,
                                @PathVariable String code,
                                HttpServletRequest request) {
        applications.acceptAgreement(principal.user().getId(), code,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleViolationException("No se pudo leer el archivo");
        }
    }
}
