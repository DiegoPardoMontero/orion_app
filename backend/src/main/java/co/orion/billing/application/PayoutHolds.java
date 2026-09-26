package co.orion.billing.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;

/**
 * Por qué la liquidación de un profe no se puede pagar todavía. Con un motivo, queda retenida
 * ({@code ON_HOLD}) y el admin lo ve dicho en palabras; cuando el profe lo resuelve, vuelve a
 * borrador (brief de liquidaciones, regla 11).
 *
 * <p>El primer motivo es el mandato: sin la versión vigente del acuerdo del profesor aceptada (la 2.0
 * trae el mandato de recaudo), Orión no tiene la constancia de que recibe ese dinero por su cuenta.
 * Sus clases se pueden seguir reservando; lo que no se le paga es la liquidación.
 */
@Service
public class PayoutHolds {

    public static final String SIN_MANDATO = "Falta aceptar el acuerdo del profesor";

    private final LegalDocumentService legal;

    public PayoutHolds(LegalDocumentService legal) {
        this.legal = legal;
    }

    /** El motivo de retención, o vacío si la liquidación de este profe se puede pagar. */
    @Transactional(readOnly = true)
    public Optional<String> motivo(UUID professorId) {
        if (!legal.aceptoLaVigente(professorId, LegalDocumentCode.TEACHER_AGREEMENT)) {
            return Optional.of(SIN_MANDATO);
        }
        return Optional.empty();
    }
}
