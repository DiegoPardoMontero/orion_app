package co.orion.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita @Async. Lo usa el envío de correos de reserva: así el commit responde de inmediato y el
 * correo sale en un hilo aparte, sin que su latencia (ni un SMTP lento) haga esperar al usuario.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
