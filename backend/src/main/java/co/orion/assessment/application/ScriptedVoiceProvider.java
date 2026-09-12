package co.orion.assessment.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El proveedor de mentira: no llama a nadie y devuelve una sesión sembrada.
 *
 * <p>Es el que corre en local y en los 392 tests de integración. Ninguno de ellos puede depender de
 * una llamada de red —ni de un saldo en una cuenta de terceros— y eso no se rompe aquí.
 *
 * <p>Es también el valor por defecto: si nadie configura un proveedor real, Orión no se queda sin
 * arrancar ni empieza a gastar dinero por accidente. Se queda con este.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted",
        matchIfMissing = true)
public class ScriptedVoiceProvider implements VoiceConversationProvider {

    static final String NAME = "scripted";

    private final Clock clock;

    public ScriptedVoiceProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public VoiceSession start(VoiceSessionRequest request) {
        String ref = "scripted-" + UUID.randomUUID();
        return new VoiceSession(
                ref,
                "ek_scripted_" + ref,
                clock.instant().plus(Duration.ofSeconds(request.maxSeconds())),
                "scripted-model");
    }

    @Override
    public void stop(String sessionRef) {
        // No hay nada que cerrar: nunca se abrió nada.
    }

    @Override
    public String name() {
        return NAME;
    }
}
