package co.orion.assessment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.assessment.persistence.AssessmentLeadRepository;
import co.orion.catalog.application.PlatformSettingsService;

/**
 * Borra el diagnóstico de quien nunca creó su cuenta.
 *
 * <p>Pasado {@code assessment_lead_retention_days} sin reclamarse, se va el lead entero: su nombre,
 * sus autorizaciones y, por la FK en cascada, sus diagnósticos con lo que dijo. Quedarse con la voz
 * transcrita de un desconocido que no volvió no tiene ninguna finalidad que la justifique.
 */
@Component
public class LeadRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LeadRetentionJob.class);

    static final String RETENCION = "assessment_lead_retention_days";

    private final AssessmentLeadRepository leads;
    private final PlatformSettingsService settings;
    private final Clock clock;

    public LeadRetentionJob(AssessmentLeadRepository leads, PlatformSettingsService settings,
                            Clock clock) {
        this.leads = leads;
        this.settings = settings;
        this.clock = clock;
    }

    /** De madrugada, junto a la otra retención del diagnóstico. */
    @Scheduled(cron = "0 35 3 * * *", zone = "America/Bogota")
    public void run() {
        int borrados = purgar();
        if (borrados > 0) {
            log.info("Retención: {} diagnóstico(s) sin cuenta borrados por plazo.", borrados);
        }
    }

    @Transactional
    public int purgar() {
        Instant limite = clock.instant().minus(Duration.ofDays(settings.getInt(RETENCION)));
        return leads.deleteUnclaimedBefore(limite);
    }
}
