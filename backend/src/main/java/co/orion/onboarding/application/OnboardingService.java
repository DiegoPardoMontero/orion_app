package co.orion.onboarding.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.application.ProfessorAccessService;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.onboarding.domain.OnboardingStep;
import co.orion.onboarding.domain.OnboardingStepCompletedEvent;
import co.orion.shared.error.UnprocessableException;

/**
 * La bienvenida: qué le falta ver a cada quien. El video de Sofía es para el profesor ya aprobado
 * —antes de la aprobación no tiene nada que enseñar todavía— y solo si hay un video configurado;
 * los recorridos, para cada rol el suyo.
 *
 * <p>Los profesores aprobados antes de que existiera el video también lo ven, una vez: la marca es
 * «lo vio», no «lo aprobaron después de tal fecha». Y si el enlace está vacío no se marca nada,
 * así que el día que Pardo pegue el video les aparece a todos.
 */
@Service
public class OnboardingService {

    static final String VIDEO = "professor_welcome_video_url";

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final ProfessorAccessService acceso;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OnboardingService(JdbcTemplate jdbc, PlatformSettingsService settings,
                             ProfessorAccessService acceso, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.acceso = acceso;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param video     {@code null} si a esta persona no le toca: no es profesor aprobado o no hay video
     * @param recorrido el recorrido que le falta ver, o {@code null}. El del profesor espera a la
     *                  aprobación: antes, la mitad de lo que muestra todavía no es suyo.
     */
    public record Estado(Video video, OnboardingStep recorrido, Set<OnboardingStep> vistos) {
    }

    public record Video(String url, boolean visto) {
    }

    @Transactional(readOnly = true)
    public Estado estado(User quien) {
        Set<OnboardingStep> vistos = EnumSet.noneOf(OnboardingStep.class);
        jdbc.query("select step from onboarding_steps where user_id = ?",
                rs -> {
                    vistos.add(OnboardingStep.valueOf(rs.getString(1)));
                }, quien.getId());
        return new Estado(video(quien, vistos.contains(OnboardingStep.WELCOME_VIDEO)),
                recorridoPendiente(quien, vistos), vistos);
    }

    private OnboardingStep recorridoPendiente(User quien, Set<OnboardingStep> vistos) {
        OnboardingStep suyo = switch (quien.getRole()) {
            case STUDENT -> OnboardingStep.TOUR_STUDENT;
            case PROFESSOR -> acceso.isApproved(quien.getId()) ? OnboardingStep.TOUR_PROFESSOR : null;
            default -> null;
        };
        return suyo == null || vistos.contains(suyo) ? null : suyo;
    }

    /** Marca un paso como visto. Idempotente: la segunda vez no cambia nada ni la fecha. */
    @Transactional
    public void completar(User quien, OnboardingStep paso) {
        if (!paso.esDe(quien.getRole())) {
            throw new UnprocessableException("Ese paso de la bienvenida no es de tu tipo de cuenta.");
        }
        int nuevas = jdbc.update("""
                insert into onboarding_steps (user_id, step, completed_at) values (?, ?, ?)
                on conflict (user_id, step) do nothing
                """, quien.getId(), paso.name(), Timestamp.from(clock.instant()));
        if (nuevas > 0) {
            events.publishEvent(new OnboardingStepCompletedEvent(quien.getId(), paso));
        }
    }

    /** Si esa persona ya terminó ese paso. */
    @Transactional(readOnly = true)
    public boolean completo(UUID userId, OnboardingStep paso) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from onboarding_steps where user_id = ? and step = ?)",
                Boolean.class, userId, paso.name()));
    }

    private Video video(User quien, boolean visto) {
        if (quien.getRole() != UserRole.PROFESSOR || !acceso.isApproved(quien.getId())) {
            return null;
        }
        String url = settings.getString(VIDEO).trim();
        return url.isEmpty() ? null : new Video(url, visto);
    }
}
