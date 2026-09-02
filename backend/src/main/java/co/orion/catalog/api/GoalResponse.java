package co.orion.catalog.api;

import co.orion.catalog.domain.TeachingGoal;

public record GoalResponse(String code, String nameEs, String nameEn) {

    public static GoalResponse from(TeachingGoal goal) {
        return new GoalResponse(goal.getCode(), goal.getNameEs(), goal.getNameEn());
    }
}
