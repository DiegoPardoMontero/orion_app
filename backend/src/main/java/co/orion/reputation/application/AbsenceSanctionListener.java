package co.orion.reputation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.lifecycle.application.DisputeResolved;

/**
 * Una ausencia confirmada dispara la evaluación de sanciones. Por evento y AFTER_COMMIT, como el
 * resto: la sanción solo se evalúa si el reclamo se resolvió de verdad, y un fallo aquí no puede
 * deshacer la devolución que el estudiante ya tiene.
 */
@Component
public class AbsenceSanctionListener {

    private static final Logger log = LoggerFactory.getLogger(AbsenceSanctionListener.class);

    private final SanctionService sanctions;

    public AbsenceSanctionListener(SanctionService sanctions) {
        this.sanctions = sanctions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDisputeResolved(DisputeResolved event) {
        if (!event.absenceRecorded()) {
            return;   // el reclamo se resolvió a favor del profesor: no hay nada que sancionar
        }
        try {
            sanctions.evaluateAfterAbsence(event.professorId());
        } catch (RuntimeException ex) {
            log.error("No se pudo evaluar la sanción del profesor {}", event.professorId(), ex);
        }
    }
}
