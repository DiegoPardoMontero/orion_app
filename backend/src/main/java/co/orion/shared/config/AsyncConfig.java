package co.orion.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Async. Lo usa el envío de correos de reserva: así el commit responde de inmediato y el
 * correo sale en un hilo aparte, sin que su latencia (ni un SMTP lento) haga esperar al usuario.
 *
 * Y @Scheduled, que necesita el barrido de reservas sin pagar: la pasarela a veces no responde
 * nunca (el estudiante cierra la pestaña de PSE) y sin ese barrido el cupo quedaría bloqueado.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
