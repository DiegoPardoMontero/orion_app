package co.orion.onboarding.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.onboarding.application.OnboardingService;
import co.orion.onboarding.domain.OnboardingStep;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.security.OrionUserDetails;

/** La bienvenida de cada usuario: el video de Sofía y los recorridos que ya vio. */
@RestController
public class OnboardingController {

    private final OnboardingService bienvenida;

    public OnboardingController(OnboardingService bienvenida) {
        this.bienvenida = bienvenida;
    }

    @GetMapping("/api/v1/me/onboarding")
    public OnboardingResponse estado(@AuthenticationPrincipal OrionUserDetails principal) {
        OnboardingService.Estado e = bienvenida.estado(principal.user());
        return new OnboardingResponse(
                e.video() == null ? null : new VideoResponse(e.video().url(), e.video().visto()),
                e.recorrido() == null ? null : e.recorrido().name(),
                e.vistos().stream().map(Enum::name).sorted().toList());
    }

    @PostMapping("/api/v1/me/onboarding/{step}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completar(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable String step) {
        bienvenida.completar(principal.user(), paso(step));
    }

    private static OnboardingStep paso(String step) {
        try {
            return OnboardingStep.valueOf(step);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Paso de bienvenida desconocido");
        }
    }

    /**
     * @param welcomeVideo ausente si a esta persona no le toca
     * @param pendingTour  el recorrido que le falta ver, si hay uno
     */
    public record OnboardingResponse(VideoResponse welcomeVideo, String pendingTour, List<String> completed) {
    }

    public record VideoResponse(String url, boolean seen) {
    }
}
