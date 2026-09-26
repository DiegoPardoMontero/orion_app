package co.orion.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.BreBKeyType;
import co.orion.billing.domain.IdDocumentType;
import co.orion.billing.domain.PayoutDestination;
import co.orion.billing.domain.PayoutDetailsChangedEvent;
import co.orion.billing.domain.ProfessorPayoutDetails;
import co.orion.billing.persistence.ProfessorPayoutDetailsRepository;
import co.orion.shared.error.BusinessRuleViolationException;

/**
 * Los datos de pago del profe (brief de liquidaciones, paso 2). El profe los escribe y los ve
 * enmascarados; completos solo los ve el admin, en el flujo de pago de una liquidación.
 */
@Service
public class PayoutDetailsService {

    private final ProfessorPayoutDetailsRepository details;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PayoutDetailsService(ProfessorPayoutDetailsRepository details, ApplicationEventPublisher events,
                                Clock clock) {
        this.details = details;
        this.events = events;
        this.clock = clock;
    }

    /** Lo que ve el profe: enmascarado, o vacío si todavía no los registró. */
    @Transactional(readOnly = true)
    public Optional<Masked> mine(UUID professorId) {
        return details.findById(professorId).map(PayoutDetailsService::masked);
    }

    /** Guarda o cambia los datos, y le avisa al profe por correo (después del commit). */
    @Transactional
    public Masked save(UUID professorId, BreBKeyType keyType, String key, IdDocumentType documentType,
                       String documentNumber, String holderName) {
        PayoutDestination destino;
        try {
            destino = PayoutDestination.of(keyType, key, documentType, documentNumber, holderName);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException(ex.getMessage());
        }
        Instant ahora = clock.instant();
        Optional<ProfessorPayoutDetails> existentes = details.findById(professorId);
        ProfessorPayoutDetails guardados = existentes
                .map(d -> {
                    d.cambiar(destino, ahora);
                    return d;
                })
                .orElseGet(() -> new ProfessorPayoutDetails(professorId, destino, ahora));
        details.save(guardados);
        events.publishEvent(new PayoutDetailsChangedEvent(professorId, destino.keyType().etiqueta(),
                destino.maskedKey(), destino.holderName(), ahora, existentes.isEmpty()));
        return masked(guardados);
    }

    /** Los datos completos. Solo para el flujo de pago del admin, que deja constancia de haberlos visto. */
    @Transactional(readOnly = true)
    public Optional<PayoutDestination> full(UUID professorId) {
        return details.findById(professorId).map(ProfessorPayoutDetails::destino);
    }

    @Transactional(readOnly = true)
    public boolean registered(UUID professorId) {
        return details.existsById(professorId);
    }

    private static Masked masked(ProfessorPayoutDetails d) {
        PayoutDestination destino = d.destino();
        return new Masked(destino.keyType(), destino.keyType().etiqueta(), destino.maskedKey(), destino.documentType(),
                destino.maskedDocument(), destino.holderName(), d.getUpdatedAt());
    }

    public record Masked(BreBKeyType keyType, String keyTypeLabel, String maskedKey, IdDocumentType documentType,
                         String maskedDocument, String holderName, Instant updatedAt) {
    }
}
