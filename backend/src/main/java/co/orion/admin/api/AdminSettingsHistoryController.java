package co.orion.admin.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.catalog.api.SettingChangeResponse;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.catalog.domain.PlatformSettingChange;
import co.orion.catalog.domain.SettingDefinition;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;

/**
 * El historial de cambios de ajustes, con el nombre de quien los hizo.
 *
 * <p>Vive en {@code admin} y no junto al resto de ajustes porque necesita cruzar {@code catalog}
 * con {@code identity}, e {@code identity} ya depende de {@code catalog}: ponerlo allí habría
 * cerrado un ciclo entre los dos módulos. {@code admin} depende de todos y de él no depende nadie,
 * que es exactamente para lo que existe.
 */
@RestController
@RequestMapping("/api/v1/admin/settings/changes")
public class AdminSettingsHistoryController {

    private final PlatformSettingsService settings;
    private final UserRepository users;

    public AdminSettingsHistoryController(PlatformSettingsService settings, UserRepository users) {
        this.settings = settings;
        this.users = users;
    }

    @GetMapping
    public List<SettingChangeResponse> changes() {
        List<PlatformSettingChange> recientes = settings.recentChanges();
        Map<UUID, String> nombres = users
                .findAllById(recientes.stream().map(PlatformSettingChange::getChangedBy).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return recientes.stream()
                .map(c -> SettingChangeResponse.from(c,
                        SettingDefinition.forKey(c.getKey())
                                .map(SettingDefinition::getEtiqueta).orElse(c.getKey()),
                        nombres.getOrDefault(c.getChangedBy(), "—")))
                .toList();
    }
}
