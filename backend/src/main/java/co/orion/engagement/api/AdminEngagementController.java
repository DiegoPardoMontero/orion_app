package co.orion.engagement.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.engagement.application.AchievementService;

/**
 * Recalcular la gamificación de un estudiante desde cero.
 *
 * <p>Es la salida cuando un evento se pierde: en vez de una migración de datos, un botón. Es seguro
 * llamarlo tantas veces como haga falta — el índice único del libro de puntos hace que reprocesar
 * no duplique nada, y hay un test que comprueba que el resultado es idéntico al del camino
 * incremental.
 */
@RestController
@RequestMapping("/api/v1/admin/engagement")
public class AdminEngagementController {

    private final AchievementService achievements;

    public AdminEngagementController(AchievementService achievements) {
        this.achievements = achievements;
    }

    @PostMapping("/recompute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recompute(@RequestParam UUID userId) {
        achievements.recompute(userId);
    }
}
