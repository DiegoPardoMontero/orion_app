package co.orion.assessment.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * La traducción de local y de los tests, junto al proveedor de voz falso: no sale a la red y marca
 * la frase para que se note que es de mentira. Sin voz de verdad tampoco hay conversación que
 * traducir, así que en local nadie la ve.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted",
        matchIfMissing = true)
public class TraductorSinRed implements TraductorDeFrases {

    @Override
    public Optional<String> alEspanol(UUID actorId, String frase) {
        return Optional.of("[es] " + frase);
    }
}
