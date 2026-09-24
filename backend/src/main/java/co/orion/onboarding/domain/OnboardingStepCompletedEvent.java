package co.orion.onboarding.domain;

import java.util.UUID;

/** Alguien terminó un paso de la bienvenida por primera vez. La segunda vez no se publica. */
public record OnboardingStepCompletedEvent(UUID userId, OnboardingStep step) {
}
