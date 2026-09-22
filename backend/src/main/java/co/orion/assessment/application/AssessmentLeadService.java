package co.orion.assessment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.assessment.domain.AssessmentLead;
import co.orion.assessment.domain.AssessmentStatus;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.assessment.domain.VoiceConsent;
import co.orion.assessment.persistence.AssessmentLeadRepository;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.assessment.persistence.VoiceConsentRepository;
import co.orion.identity.domain.User;

/**
 * El diagnóstico sin cuenta: crear el lead, reconocerlo por su llave y pasarlo a una cuenta.
 *
 * <p><strong>La llave es del dispositivo, no de la persona.</strong> Va en la cookie
 * {@value #COOKIE} (httpOnly, SameSite=Lax) y aquí solo se guarda su hash. Quien borra las
 * cookies pierde su diagnóstico sin cuenta; es el precio de no pedir nada antes de hablar, y el
 * resultado ya le ofrece guardarlo.
 *
 * <p><strong>Reclamar es mudar, no copiar.</strong> Los diagnósticos pasan a la cuenta con su
 * secuencia en orden, y la autorización de voz se copia a {@code voice_consents} con su fecha y su
 * IP originales: la constancia del art. 9 del Decreto 1377 es la de cuando la persona la dio, no
 * la de cuando entró. Si la cuenta ya tenía una autorización vigente, se respeta esa.
 */
@Service
public class AssessmentLeadService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentLeadService.class);

    public static final String COOKIE = "ORION_LEAD";

    private final AssessmentLeadRepository leads;
    private final ConfidenceAssessmentRepository assessments;
    private final VoiceConsentRepository consents;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AssessmentLeadService(AssessmentLeadRepository leads,
                                 ConfidenceAssessmentRepository assessments,
                                 VoiceConsentRepository consents,
                                 Clock clock) {
        this.leads = leads;
        this.assessments = assessments;
        this.consents = consents;
        this.clock = clock;
    }

    /** El lead recién creado y su llave en claro, que solo existe en la respuesta. */
    public record Creado(AssessmentLead lead, String llave) {
    }

    @Transactional
    public Creado crear(String nombre, String ip, String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String llave = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AssessmentLead lead = leads.save(new AssessmentLead(nombre, sha256Hex(llave),
                AssessmentService.VERSION_CONSENTIMIENTO, clock.instant(), ip, userAgent));
        return new Creado(lead, llave);
    }

    /** El lead de esta llave, si existe y nadie lo ha reclamado todavía. */
    @Transactional(readOnly = true)
    public Optional<AssessmentLead> porLlave(String llave) {
        if (llave == null || llave.isBlank()) {
            return Optional.empty();
        }
        return leads.findByTokenHash(sha256Hex(llave)).filter(l -> !l.isClaimed());
    }

    /**
     * Pasa los diagnósticos del lead a la cuenta. Idempotente: un lead ya reclamado, o una llave
     * que no existe, no hace nada.
     */
    @Transactional
    public void reclamar(String llave, User cuenta) {
        Optional<AssessmentLead> encontrado = porLlave(llave);
        if (encontrado.isEmpty()) {
            return;
        }
        AssessmentLead lead = encontrado.get();
        Instant ahora = clock.instant();

        List<ConfidenceAssessment> suyos = assessments.findByLeadIdAndUserIdIsNull(lead.getId())
                .stream().sorted(Comparator.comparing(ConfidenceAssessment::getStartedAt)).toList();
        for (ConfidenceAssessment evaluacion : suyos) {
            // Una a medias no se muda viva: la cuenta puede tener ya otra abierta en el mismo
            // idioma, y el índice único no admite dos.
            if (evaluacion.getStatus() == AssessmentStatus.IN_PROGRESS) {
                evaluacion.abandon(0, 0);
            }
            int siguiente = assessments.lastSequence(cuenta.getId(), evaluacion.getLanguageCode()) + 1;
            evaluacion.assignTo(cuenta.getId(), siguiente);
            assessments.saveAndFlush(evaluacion);
        }

        boolean yaAutorizo = consents.findFirstByUserIdOrderByAcceptedAtDesc(cuenta.getId())
                .filter(VoiceConsent::isLive).isPresent();
        if (!yaAutorizo) {
            consents.save(new VoiceConsent(cuenta.getId(), lead.getConsentVersion(),
                    lead.getConsentAcceptedAt(), lead.getConsentIp(), lead.getConsentUserAgent()));
        }

        lead.claim(cuenta.getId(), ahora);
        leads.save(lead);
        log.info("Lead {} reclamado por la cuenta {} con {} diagnóstico(s).",
                lead.getId(), cuenta.getId(), suyos.size());
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
