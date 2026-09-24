package co.orion.practice.domain;

import java.util.UUID;

/** Una práctica lista que nadie empezó: toca recordarla, una sola vez. */
public record PracticeReminderDueEvent(UUID setId, UUID studentId, UUID professorId, int itemCount) {
}
