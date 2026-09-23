package co.orion.teaching.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El dictado de local y de los tests: no sale a la red y devuelve una frase fija, para que la
 * pantalla y las reglas se puedan probar sin micrófono ni proveedor.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted",
        matchIfMissing = true)
public class TranscriptorSinRed implements TranscriptorDeDictado {

    @Override
    public Optional<String> transcribir(UUID profesorId, byte[] audio, String tipo, int segundos) {
        return Optional.of("Trabajamos past simple y le costó 'used to' (dictado de prueba).");
    }
}
