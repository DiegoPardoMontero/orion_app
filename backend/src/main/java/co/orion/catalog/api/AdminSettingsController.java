package co.orion.catalog.api;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** Ajustes de plataforma para el admin. La protección /api/v1/admin/** la da SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

    private final PlatformSettingsService settings;

    public AdminSettingsController(PlatformSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public List<SettingResponse> list() {
        return settings.all().stream()
                .map(SettingResponse::from)
                // Por grupo y, dentro, por etiqueta: la pantalla los pinta en el orden en que
                // llegan y así no tiene que saberse el catálogo.
                .sorted(Comparator.comparing(SettingResponse::group)
                        .thenComparing(SettingResponse::label))
                .toList();
    }

    @PutMapping("/{key}")
    public SettingResponse update(@PathVariable String key,
                                  @Valid @RequestBody UpdateSettingRequest body,
                                  @AuthenticationPrincipal OrionUserDetails principal) {
        return SettingResponse.from(settings.update(key, body.value(), principal.user().getId()));
    }
}
