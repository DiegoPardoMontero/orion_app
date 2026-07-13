package co.orion.identity.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Smoke test permanente de la protección por rol: solo ADMIN debería llegar aquí. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminPingController {

    @GetMapping("/ping")
    public Map<String, Boolean> ping() {
        return Map.of("pong", true);
    }
}
