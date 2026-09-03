package co.orion.scheduling.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.StudentProgressService;
import co.orion.shared.security.OrionUserDetails;

/** El panel de progreso del estudiante. Solo lectura, y solo de lo suyo. */
@RestController
@RequestMapping("/api/v1/me/progress")
public class MyProgressController {

    private final StudentProgressService progress;

    public MyProgressController(StudentProgressService progress) {
        this.progress = progress;
    }

    @GetMapping
    public MyProgressResponse myProgress(@AuthenticationPrincipal OrionUserDetails principal) {
        return MyProgressResponse.from(progress.of(principal.user()));
    }
}
