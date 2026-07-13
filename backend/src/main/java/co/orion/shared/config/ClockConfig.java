package co.orion.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * Toda lectura de la hora pasa por este bean, nunca por {@code Instant.now()} directo:
     * así los tests pueden congelar el tiempo sustituyéndolo por un {@code Clock.fixed(...)}.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
