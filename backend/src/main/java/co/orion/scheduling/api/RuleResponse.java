package co.orion.scheduling.api;

import java.time.LocalTime;
import java.util.UUID;

import co.orion.scheduling.domain.AvailabilityRule;

public record RuleResponse(UUID id, int weekday, LocalTime startTime, LocalTime endTime, boolean active) {

    public static RuleResponse from(AvailabilityRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getWeekday().getValue(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.isActive());
    }
}
