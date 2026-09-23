package co.orion.assessment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.assessment.domain.VoiceConsent;
import co.orion.assessment.persistence.AssessmentTurnRepository;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.assessment.persistence.VoiceConsentRepository;
import co.orion.catalog.application.PlatformSettingsService;

/**
 * Borra las transcripciones cuando toca. Conserva el puntaje, las señales y el resumen.
 *
 * <p>La distinción es el punto entero: la curva de progreso de alguien entre un diagnóstico y el
 * siguiente sobrevive, y el texto de lo que dijo mientras practicaba, no. Guardar indefinidamente
 * la transcripción de una persona insegura hablando un idioma que no domina no tiene justificación
 * — ni de producto ni, pasado el plazo, legal.
 *
 * <p>Dos disparadores. El plazo de {@code assessment_transcript_retention_days}, y la
 * <strong>revocación del consentimiento</strong>, que no espera al plazo: quien retira su
 * autorización deja de tener texto nuestro en la siguiente corrida.
 *
 * <p>Se borra el texto, no la fila. Borrar el turno se llevaría por delante las señales, que son
 * justo lo que sí se puede conservar: son números sobre cómo habló, no lo que dijo.
 *
 * <p>La transacción va por {@code TransactionTemplate} y no por {@code @Transactional}: el
 * programador llama a {@link #run()}, y una anotación en {@link #purgar()} no se aplica desde dentro
 * de la misma clase. Así estuvo hasta el 23/09/2026 y cada corrida moría con «No active transaction
 * for update or delete query» sin borrar nada.
 */
@Component
public class TranscriptRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(TranscriptRetentionJob.class);

    private static final String RETENCION = "assessment_transcript_retention_days";

    private final ConfidenceAssessmentRepository assessments;
    private final AssessmentTurnRepository turns;
    private final VoiceConsentRepository consents;
    private final PlatformSettingsService settings;
    private final TransactionTemplate enTransaccion;
    private final Clock clock;

    public TranscriptRetentionJob(ConfidenceAssessmentRepository assessments,
                                  AssessmentTurnRepository turns,
                                  VoiceConsentRepository consents,
                                  PlatformSettingsService settings,
                                  PlatformTransactionManager transacciones,
                                  Clock clock) {
        this.assessments = assessments;
        this.turns = turns;
        this.consents = consents;
        this.settings = settings;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.clock = clock;
    }

    /** De madrugada, hora de Bogotá: es cuando menos gente está en mitad de una conversación. */
    @Scheduled(cron = "0 20 3 * * *", zone = "America/Bogota")
    public void run() {
        int borradas = purgar();
        if (borradas > 0) {
            log.info("Retención: transcripción borrada en {} turnos.", borradas);
        }
    }

    public int purgar() {
        Integer borradas = enTransaccion.execute(estado -> borrarLoQueToca());
        return borradas == null ? 0 : borradas;
    }

    private int borrarLoQueToca() {
        Instant limite = clock.instant()
                .minus(Duration.ofDays(settings.getInt(RETENCION)));

        List<UUID> porPlazo = assessments.idsOlderThan(limite);

        // Y los de quien revocó, sin esperar al plazo. Retirar la autorización tiene que significar
        // algo el mismo día, no dentro de un año.
        List<UUID> deRevocados = consents.findByRevokedAtIsNotNull().stream()
                .map(VoiceConsent::getUserId)
                .distinct()
                .flatMap(userId -> assessments.findByUserIdOrderByStartedAtDesc(userId).stream())
                .map(a -> a.getId())
                .toList();

        List<UUID> todos = java.util.stream.Stream.concat(porPlazo.stream(), deRevocados.stream())
                .distinct().toList();

        return todos.isEmpty() ? 0 : turns.scrubTranscripts(todos);
    }
}
