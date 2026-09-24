package co.orion.practice.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Sin IA (local y pruebas): nadie revisa y manda la regla de {@code Evaluador}. */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted", matchIfMissing = true)
public class RevisorSinIa implements RevisorDeFrases {

    @Override
    public Optional<Boolean> acepta(UUID estudianteId, String termino, String frase) {
        return Optional.empty();
    }
}
