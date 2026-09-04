package co.orion.engagement.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;

/**
 * Enciende las estrellas de los estudiantes que ya existían.
 *
 * <p>Sin esto, el primer contacto de todo el mundo con la función sería un cielo completamente
 * vacío pese a llevar meses tomando clases — que además de injusto es la peor presentación posible.
 *
 * <p>Corre una sola vez, al arrancar, y es seguro que corra de más: {@code recompute} es idempotente
 * gracias al índice único de {@code point_events}, así que reprocesar no duplica puntos. Por eso no
 * hace falta una tabla de «ya se hizo»: si el despliegue se repite, el resultado es el mismo.
 *
 * <p>Se puede apagar con {@code orion.engagement.backfill-on-start=false} — útil si algún día la
 * base crece lo bastante como para que arrancar sea lento.
 */
@Component
public class EngagementBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EngagementBackfillRunner.class);

    private final UserRepository users;
    private final AchievementService achievements;
    private final boolean enabled;

    public EngagementBackfillRunner(UserRepository users,
                                    AchievementService achievements,
                                    @Value("${orion.engagement.backfill-on-start:true}") boolean enabled) {
        this.users = users;
        this.achievements = achievements;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        List<UUID> estudiantes = users.findByRole(UserRole.STUDENT).stream()
                .map(u -> u.getId())
                .toList();
        if (estudiantes.isEmpty()) {
            return;
        }

        int hechos = 0;
        for (UUID estudiante : estudiantes) {
            try {
                achievements.recompute(estudiante);
                hechos++;
            } catch (RuntimeException ex) {
                // Un estudiante con datos raros no puede impedir que arranque la aplicación.
                log.error("No se pudo recalcular la gamificación de {}", estudiante, ex);
            }
        }
        log.info("Gamificación: estado recalculado para {} de {} estudiantes",
                hechos, estudiantes.size());
    }
}
