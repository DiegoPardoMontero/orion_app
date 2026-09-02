package co.orion.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import co.orion.identity.domain.CompensationModel;
import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.UserStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Construye el {@link Specification} del buscador. Subconsultas correlacionadas por professorId. */
final class ProfessorSpecifications {

    private ProfessorSpecifications() {
    }

    static Specification<ProfessorProfile> matching(ProfessorSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Base: publicado, usuario activo, y nunca un COMMISSION sin tarifa (aparecería sin precio).
            predicates.add(cb.isTrue(root.get("published")));
            predicates.add(cb.equal(root.get("user").get("status"), UserStatus.ACTIVE));
            predicates.add(cb.or(
                    cb.equal(root.get("compensationModel"), CompensationModel.FIXED_FEE),
                    cb.isNotNull(root.get("hourlyRateCop"))));

            if (c.language() != null && !c.language().isBlank()) {
                predicates.add(hasLanguage(root, query, cb, c.language(), Boolean.TRUE.equals(c.nativeOnly())));
            } else if (Boolean.TRUE.equals(c.nativeOnly())) {
                predicates.add(hasLanguage(root, query, cb, null, true));
            }

            if (c.levels() != null && !c.levels().isEmpty()) {
                predicates.add(hasAnyLevel(root, query, cb, c.language(), c.levels()));
            }

            if (c.goals() != null && !c.goals().isEmpty()) {
                predicates.add(hasAnyGoal(root, query, cb, c.goals()));
            }

            if (c.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Long>get("hourlyRateCop"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Long>get("hourlyRateCop"), c.maxPrice()));
            }
            if (Boolean.TRUE.equals(c.certified())) {
                predicates.add(cb.isTrue(root.get("certified")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate hasLanguage(Root<ProfessorProfile> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                         String language, boolean nativeOnly) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<ProfessorLanguage> pl = sub.from(ProfessorLanguage.class);
        List<Predicate> conds = new ArrayList<>();
        conds.add(cb.equal(pl.get("professorId"), root.get("userId")));
        if (language != null && !language.isBlank()) {
            conds.add(cb.equal(pl.get("languageCode"), language));
        }
        if (nativeOnly) {
            conds.add(cb.isTrue(pl.get("isNative")));
        }
        sub.select(pl.get("professorId")).where(conds.toArray(new Predicate[0]));
        return cb.exists(sub);
    }

    private static Predicate hasAnyLevel(Root<ProfessorProfile> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                         String language, List<String> levels) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<ProfessorLanguageLevel> lvl = sub.from(ProfessorLanguageLevel.class);
        List<Predicate> conds = new ArrayList<>();
        conds.add(cb.equal(lvl.get("professorId"), root.get("userId")));
        conds.add(lvl.get("level").in(levels));
        if (language != null && !language.isBlank()) {
            conds.add(cb.equal(lvl.get("languageCode"), language));
        }
        sub.select(lvl.get("professorId")).where(conds.toArray(new Predicate[0]));
        return cb.exists(sub);
    }

    private static Predicate hasAnyGoal(Root<ProfessorProfile> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                        List<String> goals) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<ProfessorGoal> g = sub.from(ProfessorGoal.class);
        sub.select(g.get("professorId")).where(
                cb.equal(g.get("professorId"), root.get("userId")),
                g.get("goalCode").in(goals));
        return cb.exists(sub);
    }
}
