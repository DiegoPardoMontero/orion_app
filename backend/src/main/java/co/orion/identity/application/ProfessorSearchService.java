package co.orion.identity.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.domain.Language;
import co.orion.catalog.persistence.LanguageRepository;
import co.orion.identity.api.LanguageBadge;
import co.orion.identity.api.PagedProfessors;
import co.orion.identity.api.ProfessorCard;
import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.persistence.ProfessorGoalRepository;
import co.orion.identity.persistence.ProfessorLanguageLevelRepository;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.reputation.application.ProfessorRatingService;
import co.orion.reputation.application.SanctionService;
import co.orion.reputation.application.RatingSummary;

/** Buscador del marketplace: filtra, pagina y ensambla las tarjetas resolviendo idiomas por lotes. */
@Service
public class ProfessorSearchService {

    private final ProfessorProfileRepository profiles;
    private final ProfessorLanguageRepository languagesOf;
    private final ProfessorLanguageLevelRepository levelsOf;
    private final ProfessorGoalRepository goalsOf;
    private final LanguageRepository languageCatalog;
    private final ProfessorRatingService ratings;
    private final SanctionService sanctions;

    public ProfessorSearchService(ProfessorProfileRepository profiles,
                                  ProfessorLanguageRepository languagesOf,
                                  ProfessorLanguageLevelRepository levelsOf,
                                  ProfessorGoalRepository goalsOf,
                                  LanguageRepository languageCatalog,
                                  ProfessorRatingService ratings,
                                  SanctionService sanctions) {
        this.profiles = profiles;
        this.languagesOf = languagesOf;
        this.levelsOf = levelsOf;
        this.goalsOf = goalsOf;
        this.languageCatalog = languageCatalog;
        this.ratings = ratings;
        this.sanctions = sanctions;
    }

    @Transactional(readOnly = true)
    public PagedProfessors search(ProfessorSearchCriteria criteria, ProfessorSortOption sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sortOf(sort));
        // Los sancionados con perfil oculto se resuelven aquí y entran ya como una lista de ids.
        ProfessorSearchCriteria effective = criteria.hiding(sanctions.hiddenProfessorIds());
        Page<ProfessorProfile> found = profiles.findAll(ProfessorSpecifications.matching(effective), pageable);

        List<UUID> ids = found.getContent().stream().map(ProfessorProfile::getUserId).toList();
        Map<UUID, List<ProfessorLanguage>> langs = ids.isEmpty() ? Map.of()
                : languagesOf.findByProfessorIdIn(ids).stream().collect(Collectors.groupingBy(ProfessorLanguage::getProfessorId));
        Map<UUID, List<ProfessorLanguageLevel>> levels = ids.isEmpty() ? Map.of()
                : levelsOf.findByProfessorIdIn(ids).stream().collect(Collectors.groupingBy(ProfessorLanguageLevel::getProfessorId));
        Map<UUID, List<ProfessorGoal>> goals = ids.isEmpty() ? Map.of()
                : goalsOf.findByProfessorIdIn(ids).stream().collect(Collectors.groupingBy(ProfessorGoal::getProfessorId));
        Map<String, Language> catalog = languageCatalog.findAll().stream()
                .collect(Collectors.toMap(Language::getCode, l -> l));
        // Métricas de la página en una sola consulta; los ausentes rinden RatingSummary.EMPTY.
        Map<UUID, RatingSummary> ratingsByProfessor = ratings.summariesFor(ids);

        List<ProfessorCard> cards = found.getContent().stream()
                .map(p -> toCard(p, langs, levels, goals, catalog, ratingsByProfessor))
                .toList();

        // sort=RATING: la consulta trae el orden estable de RELEVANCE; reordenamos la página en
        // memoria por promedio DESC (nulls al final). A nuestro volumen, ordenar la página basta.
        if (sort == ProfessorSortOption.RATING) {
            cards = cards.stream()
                    .sorted(Comparator.comparing(ProfessorCard::ratingAvg,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(ProfessorCard::ratingCount, Comparator.reverseOrder()))
                    .toList();
        }

        return new PagedProfessors(cards, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    private ProfessorCard toCard(ProfessorProfile p,
                                 Map<UUID, List<ProfessorLanguage>> langs,
                                 Map<UUID, List<ProfessorLanguageLevel>> levels,
                                 Map<UUID, List<ProfessorGoal>> goals,
                                 Map<String, Language> catalog,
                                 Map<UUID, RatingSummary> ratingsByProfessor) {
        List<LanguageBadge> badges = langs.getOrDefault(p.getUserId(), List.of()).stream()
                .map(pl -> {
                    Language l = catalog.get(pl.getLanguageCode());
                    return new LanguageBadge(pl.getLanguageCode(),
                            l == null ? pl.getLanguageCode() : l.getNameEs(),
                            l == null ? pl.getLanguageCode() : l.getNameEn(),
                            l == null ? null : l.getFlagEmoji(),
                            pl.isNative());
                })
                .toList();
        List<String> levelCodes = levels.getOrDefault(p.getUserId(), List.of()).stream()
                .map(ProfessorLanguageLevel::getLevel).distinct().sorted().toList();
        List<String> goalCodes = goals.getOrDefault(p.getUserId(), List.of()).stream()
                .map(ProfessorGoal::getGoalCode).sorted().toList();

        RatingSummary rating = ratingsByProfessor.getOrDefault(p.getUserId(), RatingSummary.EMPTY);
        return new ProfessorCard(
                p.getUserId(),
                p.getUser().getFullName(),
                p.getUser().getPhotoUrl(),
                p.getHeadline(),
                p.getCity(),
                p.getCountryCode(),
                p.isCertified(),
                p.getHourlyRateCop(),
                rating.ratingAvg(),
                rating.ratingCount(),
                badges,
                levelCodes,
                goalCodes);
    }

    private Sort sortOf(ProfessorSortOption sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("hourlyRateCop"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("hourlyRateCop"));
            // RATING trae el orden estable de RELEVANCE desde la BD y luego se reordena la página en
            // memoria por promedio (el agregado vive en otra tabla del módulo reputation).
            case RELEVANCE, RATING -> Sort.by(Sort.Order.desc("certified"), Sort.Order.asc("userId"));
        };
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 12;
        }
        return Math.min(size, 48);
    }
}
