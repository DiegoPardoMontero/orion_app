package co.orion.shared.config;

import java.time.Clock;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Hace que @CreatedDate y @LastModifiedDate lean la hora del bean Clock y no del reloj
     * del sistema, para que un Clock fijo en tests también congele las marcas de auditoría.
     */
    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of((TemporalAccessor) clock.instant());
    }
}
