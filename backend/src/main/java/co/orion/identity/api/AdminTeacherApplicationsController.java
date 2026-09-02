package co.orion.identity.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.TeacherApplicationService;
import co.orion.shared.security.OrionUserDetails;

/** Bandeja y revisión de postulaciones. Solo ADMIN (la ruta /admin/** ya lo exige). */
@RestController
@RequestMapping("/api/v1/admin/teacher-applications")
public class AdminTeacherApplicationsController {

    private final TeacherApplicationService applications;

    public AdminTeacherApplicationsController(TeacherApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public PagedApplications list(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return applications.list(status, page, size);
    }

    @GetMapping("/{id}")
    public AdminApplicationDetail detail(@PathVariable UUID id) {
        return applications.detail(id);
    }

    @PostMapping("/{id}/start-review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startReview(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        applications.startReview(id, principal.user().getId());
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        applications.approve(id, principal.user().getId());
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal OrionUserDetails principal,
                       @PathVariable UUID id,
                       @RequestBody ReviewDecisionRequest body) {
        applications.reject(id, principal.user().getId(), body.note());
    }

    @PostMapping("/{id}/request-changes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestChanges(@AuthenticationPrincipal OrionUserDetails principal,
                               @PathVariable UUID id,
                               @RequestBody ReviewDecisionRequest body) {
        applications.requestChanges(id, principal.user().getId(), body.note());
    }
}
