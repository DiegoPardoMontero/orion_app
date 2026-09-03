package co.orion.reputation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.reputation.domain.ProfessorSanction;
import co.orion.reputation.domain.SanctionState;
import co.orion.reputation.domain.SanctionType;
import co.orion.reputation.persistence.ProfessorSanctionRepository;
import co.orion.scheduling.persistence.ProfessorAbsenceRepository;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

/**
 * Sanciones progresivas por ausencias confirmadas.
 *
 * <h2>Modo observación</h2>
 *
 * {@code sanctions_mode} decide si el sistema APLICA la sanción o solo la PROPONE. Arranca en
 * OBSERVE por decisión de Pardo (02/09/2026) y con un motivo concreto: con un puñado de profesores,
 * un automatismo que oculta perfiles puede sacar a alguien del marketplace de madrugada sin que
 * nadie lo mire. En observación el sistema hace todo el trabajo —cuenta, calcula qué corresponde,
 * lo registra y avisa— y deja el último paso a una persona.
 *
 * Encenderlo del todo es un UPDATE a {@code platform_settings}, no un despliegue.
 */
@Service
public class SanctionService {

    private static final Logger log = LoggerFactory.getLogger(SanctionService.class);
    private static final String MODE = "sanctions_mode";
    private static final String WINDOW_DAYS = "metrics_window_days";
    private static final String ENFORCE = "ENFORCE";

    private final ProfessorSanctionRepository sanctions;
    private final ProfessorAbsenceRepository absences;
    private final PlatformSettingsService settings;
    private final Clock clock;

    public SanctionService(ProfessorSanctionRepository sanctions,
                           ProfessorAbsenceRepository absences,
                           PlatformSettingsService settings,
                           Clock clock) {
        this.sanctions = sanctions;
        this.absences = absences;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Se llama cuando queda registrada una ausencia. Cuenta las de la ventana, decide qué
     * corresponde y la crea — activa o propuesta, según el modo.
     */
    @Transactional
    public ProfessorSanction evaluateAfterAbsence(UUID professorId) {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(settings.getInt(WINDOW_DAYS)));
        long count = absences.countByProfessorIdAndOccurredAtAfter(professorId, since);

        SanctionType type = SanctionType.forAbsenceCount(count);
        boolean enforce = ENFORCE.equalsIgnoreCase(settings.getString(MODE).trim());
        SanctionState state = enforce ? SanctionState.ACTIVE : SanctionState.PROPOSED;

        String reason = count + (count == 1 ? " ausencia confirmada" : " ausencias confirmadas")
                + " en los últimos " + settings.getInt(WINDOW_DAYS) + " días";

        ProfessorSanction sanction = sanctions.save(
                new ProfessorSanction(professorId, type, reason, state, now, null));

        log.info("Sanción {} para el profesor {} ({}) — {}",
                type, professorId, state, reason);
        return sanction;
    }

    /** Cuántas sanciones le pesan ahora mismo. Lo usa el ranking para descontar visibilidad. */
    @Transactional(readOnly = true)
    public int activeCountFor(UUID professorId) {
        return sanctions.findActive(professorId, clock.instant()).size();
    }

    @Transactional(readOnly = true)
    public List<ProfessorSanction> activeFor(UUID professorId) {
        return sanctions.findActive(professorId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ProfessorSanction> historyFor(UUID professorId) {
        return sanctions.findByProfessorIdOrderByCreatedAtDesc(professorId);
    }

    /** Las que el sistema propuso y esperan que una persona decida. */
    @Transactional(readOnly = true)
    public List<ProfessorSanction> proposed() {
        return sanctions.findByStateOrderByCreatedAtDesc(SanctionState.PROPOSED);
    }

    /** ¿Este profesor puede recibir reservas nuevas? Las ya confirmadas se respetan siempre. */
    @Transactional(readOnly = true)
    public boolean acceptsNewBookings(UUID professorId) {
        return !sanctions.findBookingBlockedProfessorIds(clock.instant()).contains(professorId);
    }

    /** Los profesores que una sanción activa saca del buscador. */
    @Transactional(readOnly = true)
    public List<UUID> hiddenProfessorIds() {
        return sanctions.findHiddenProfessorIds(clock.instant());
    }

    @Transactional
    public ProfessorSanction confirm(UUID sanctionId, UUID adminId) {
        ProfessorSanction sanction = require(sanctionId);
        try {
            sanction.confirm(adminId, clock.instant());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return sanctions.save(sanction);
    }

    @Transactional
    public ProfessorSanction revoke(UUID sanctionId, UUID adminId) {
        ProfessorSanction sanction = require(sanctionId);
        try {
            sanction.revoke(adminId, clock.instant());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return sanctions.save(sanction);
    }

    /** Sanción manual del admin. Es la única vía para cerrar una cuenta. */
    @Transactional
    public ProfessorSanction applyManually(UUID professorId, String typeName, String reason, UUID adminId) {
        SanctionType type;
        try {
            type = SanctionType.valueOf(typeName.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new UnprocessableException("Tipo de sanción desconocido: " + typeName);
        }
        if (reason == null || reason.isBlank()) {
            throw new UnprocessableException("Una sanción necesita un motivo escrito");
        }
        return sanctions.save(new ProfessorSanction(
                professorId, type, reason.trim(), SanctionState.ACTIVE, clock.instant(), adminId));
    }

    private ProfessorSanction require(UUID sanctionId) {
        return sanctions.findById(sanctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sanción no encontrada"));
    }
}
