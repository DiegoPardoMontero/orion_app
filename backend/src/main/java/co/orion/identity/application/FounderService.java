package co.orion.identity.application;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.api.FounderView;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

/**
 * Otorgar y quitar el beneficio de profe fundador (brief del profe fundador, paso 2).
 *
 * <p>Otorgarlo copia al perfil la comisión y los meses vigentes en ese momento: los ajustes solo se
 * leen aquí. Quitarlo solo afecta las reservas nuevas, porque cada pago ya congeló su comisión.
 */
@Service
public class FounderService {

    public static final String RATE_KEY = "founder_commission_rate_bps";
    public static final String MONTHS_KEY = "founder_period_months";

    private final ProfessorProfileRepository profiles;
    private final UserRepository users;
    private final PlatformSettingsService settings;
    private final AdminAuditService audit;
    private final Clock clock;

    public FounderService(ProfessorProfileRepository profiles,
                          UserRepository users,
                          PlatformSettingsService settings,
                          AdminAuditService audit,
                          Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    /** Lo otorga el admin a mano, a un profe que ya existe. Si ya lo tenía, lo deja como estaba. */
    @Transactional
    public FounderView grant(UUID professorId, UUID adminId) {
        ProfessorProfile profile = profesor(professorId);
        boolean nuevo = profile.founderTerms() == null;
        otorgar(profile);
        if (nuevo) {
            audit.record(adminId, "GRANT_FOUNDER", "professor_profile", professorId,
                    "{\"rateBps\":" + profile.founderTerms().rateBps()
                            + ",\"periodMonths\":" + profile.founderTerms().periodMonths() + "}");
        }
        return FounderView.of(profile.founderTerms(), clock.instant());
    }

    /** Lo quita el admin. Las reservas que ya existen conservan su comisión. */
    @Transactional
    public void revoke(UUID professorId, UUID adminId) {
        profesor(professorId);
        if (profiles.revokeFounder(professorId) > 0) {
            audit.record(adminId, "REVOKE_FOUNDER", "professor_profile", professorId, "{}");
        }
    }

    /** Lo otorga el sistema: al aprobarse la postulación de quien entró por una invitación de fundador. */
    @Transactional
    public void grantOnApproval(UUID professorId) {
        profiles.findById(professorId).ifPresent(this::otorgar);
    }

    /** El beneficio de cada profe de la lista, para el admin; los que no son fundadores no aparecen. */
    @Transactional(readOnly = true)
    public Map<UUID, FounderView> viewsOf(Collection<UUID> professorIds) {
        return profiles.findAllById(professorIds).stream()
                .filter(p -> p.founderTerms() != null)
                .collect(Collectors.toMap(ProfessorProfile::getUserId,
                        p -> FounderView.of(p.founderTerms(), clock.instant()),
                        (a, b) -> a));
    }

    private void otorgar(ProfessorProfile profile) {
        profile.grantFounder(settings.getInt(RATE_KEY), settings.getInt(MONTHS_KEY), clock.instant());
        profiles.save(profile);
    }

    private ProfessorProfile profesor(UUID professorId) {
        boolean esProfesor = users.findById(professorId)
                .map(u -> u.getRole() == UserRole.PROFESSOR).orElse(false);
        if (!esProfesor) {
            throw new UnprocessableException("Solo un profesor puede tener el beneficio de fundador.");
        }
        return profiles.findById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
    }
}
