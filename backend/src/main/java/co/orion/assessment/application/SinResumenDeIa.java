package co.orion.assessment.application;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sin proveedor de verdad, no hay resumen de IA: el resultado usa la frase de plantilla. Es lo que
 * corre en local y en los tests, junto al proveedor de voz falso, para que ninguno toque la red.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted",
        matchIfMissing = true)
public class SinResumenDeIa implements ConversationSummarizer {

    @Override
    public Optional<String> resumir(Pedido pedido) {
        return Optional.empty();
    }
}
