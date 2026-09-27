package co.orion.identity.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.CompensationModel;
import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.UserStatus;
import co.orion.reputation.application.RatingSummary;
import co.orion.reputation.domain.ProfessorMetrics;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
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
            // El gate de visibilidad: sin una postulación APPROVED, un profesor NUNCA aparece.
            predicates.add(isApproved(root, query, cb));
            // Y una sanción activa que lo oculte lo saca del buscador mientras dure. Los ids llegan
            // ya resueltos: meter la consulta de sanciones dentro de esta Specification ataría el
            // buscador al esquema de reputation.
            if (!c.hiddenProfessorIds().isEmpty()) {
                predicates.add(cb.not(root.get("userId").in(c.hiddenProfessorIds())));
            }

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

            // Disponibilidad. Nulo es "no se pidió"; vacía es "se pidió y no lo cumple nadie", y
            // ahí el resultado tiene que ser vacío — devolver el catálogo entero justo cuando
            // alguien acaba de pedir algo muy concreto sería lo contrario de un filtro.
            if (c.availableProfessorIds() != null) {
                predicates.add(c.availableProfessorIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("userId").in(c.availableProfessorIds()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * El orden de RELEVANCE y RATING, resuelto en la base para que la paginación lo respete. Antes
     * RELEVANCE era «certificados primero y luego por id» —un orden estable pero ciego— mientras
     * «Desempeño» le decía al profe que su posición salía de un puntaje; y RATING solo reordenaba
     * la página que ya había llegado.
     *
     * <ul>
     *   <li>RELEVANCE: {@code ranking_score} de reputation (lo recalcula el job nocturno; nulo hasta
     *       su primera pasada, y ese va al final), luego certificados.</li>
     *   <li>RATING: el promedio que se EXHIBE —con menos de {@value RatingSummary#MIN_REVIEWS_FOR_AVERAGE}
     *       reseñas no hay promedio y va al final—, luego cuántas reseñas, y después lo mismo que
     *       RELEVANCE.</li>
     * </ul>
     *
     * El desempate final es el nombre y, solo entre homónimos, el id: la paginación necesita un
     * orden total, o un profe podría salir en dos páginas y otro en ninguna.
     *
     * <p>A diferencia de las sanciones, que entran ya resueltas como una lista de ids, esto sí
     * toca la tabla de reputation dentro de la consulta: un orden no se puede pasar como lista y
     * tiene que existir antes de cortar la página. Es un LEFT JOIN por la clave (uno a uno), así
     * que no cambia qué filas salen ni cuántas.
     */
    static Specification<ProfessorProfile> orderedBy(ProfessorSortOption sort) {
        return (root, query, cb) -> {
            // El conteo de la paginación no ordena: ni join ni ORDER BY para él.
            if (query == null || Long.class.equals(query.getResultType())) {
                return null;
            }
            Join<ProfessorProfile, ProfessorMetrics> metrics = root.join(ProfessorMetrics.class, JoinType.LEFT);
            metrics.on(cb.equal(metrics.get("professorId"), root.get("userId")));

            List<Order> orden = new ArrayList<>();
            if (sort == ProfessorSortOption.RATING) {
                Expression<BigDecimal> promedioVisible = cb.<BigDecimal>selectCase()
                        .when(cb.ge(metrics.<Integer>get("ratingCount"), RatingSummary.MIN_REVIEWS_FOR_AVERAGE),
                                metrics.<BigDecimal>get("ratingAvg"))
                        .otherwise(cb.nullLiteral(BigDecimal.class));
                orden.add(cb.desc(promedioVisible, Nulls.LAST));
                orden.add(cb.desc(metrics.get("ratingCount"), Nulls.LAST));
            }
            orden.add(cb.desc(metrics.get("rankingScore"), Nulls.LAST));
            orden.add(cb.desc(root.get("certified")));
            orden.add(cb.asc(root.get("user").get("fullName")));
            orden.add(cb.asc(root.get("userId")));
            query.orderBy(orden);
            return null;
        };
    }

    private static Predicate isApproved(Root<ProfessorProfile> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<TeacherApplication> app = sub.from(TeacherApplication.class);
        sub.select(app.get("userId")).where(
                cb.equal(app.get("userId"), root.get("userId")),
                cb.equal(app.get("status"), ApplicationStatus.APPROVED));
        return cb.exists(sub);
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
