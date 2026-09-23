package co.orion.assessment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.assessment.persistence.AssessmentLeadRepository;
import co.orion.catalog.application.PlatformSettingsService;

/**
 * Borra el diagnóstico de quien nunca creó su cuenta.
 *
 * <p>Pasado {@code assessment_lead_retention_days} sin reclamarse, se va el lead entero: su nombre,
 * sus autorizaciones y, por la FK en cascada, sus diagnósticos con lo que dijo. Quedarse con la voz
 * transcrita de un desconocido que no volvió no tiene ninguna finalidad que la justifique.
 *
 * <p>La transacción va por {@code TransactionTemplate} y no por {@code @Transactional}: el
 * programador llama a {@link #run()}, y una anotación en {@link #purgar()} no se aplica desde dentro
 * de la misma clase. Así estuvo hasta el 23/09/2026 y cada corrida moría con «No active transaction
 * for update or delete query» sin borrar nada.
 */
@Component
public class LeadRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LeadRetentionJob.class);

    static final String RETENCION = "assessment_lead_retention_days";

    private final AssessmentLeadRepository leads;
    private final PlatformSettingsService settings;
    private final TransactionTemplate enTransaccion;
    private final Clock clock;

    public LeadRetentionJob(AssessmentLeadRepository leads, PlatformSettingsService settings,
                            PlatformTransactionManager transacciones, Clock clock) {
        this.leads = leads;
        this.settings = settings;
        this.enTransaccion = new TransactionTemplate(transacciones);
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

    public int purgar() {
        Instant limite = clock.instant().minus(Duration.ofDays(settings.getInt(RETENCION)));
        Integer borrados = enTransaccion.execute(estado -> leads.deleteUnclaimedBefore(limite));
        return borrados == null ? 0 : borrados;
    }
}
