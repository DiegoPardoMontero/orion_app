package co.orion.billing.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import co.orion.catalog.domain.CommissionPolicy;
import co.orion.catalog.domain.FounderTerms;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.persistence.ProfessorProfileRepository;

/**
 * La primera clase pagada arranca el conteo del profe fundador (brief del profe fundador, regla 4):
 * un pago aprobado por la pasarela, una clase cubierta con saldo o la clase de prueba de $0.
 *
 * <p>Vive en billing porque es billing quien sabe cuándo algo quedó pagado, y billing ya depende de
 * identity para leer el perfil del profe: no abre una dependencia nueva. El UPDATE condicional del
 * repositorio es el árbitro; esto solo evita la escritura cuando se sabe que no hace falta.
 */
@Component
public class FounderClock {

    private final ProfessorProfileRepository profiles;

    public FounderClock(ProfessorProfileRepository profiles) {
        this.profiles = profiles;
    }

    public void onPaid(UUID professorId, Instant paidAt) {
        FounderTerms terms = profiles.findById(professorId).map(ProfessorProfile::founderTerms).orElse(null);
        if (terms == null || terms.startedAt() != null) {
            return;
        }
        profiles.startFounderClock(professorId, paidAt,
                CommissionPolicy.founderUntil(paidAt, terms.periodMonths()));
    }
}
