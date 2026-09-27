package co.orion.identity.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final ProfessorAvailabilityLookup availability;

    public ProfessorSearchService(ProfessorProfileRepository profiles,
                                  ProfessorLanguageRepository languagesOf,
                                  ProfessorLanguageLevelRepository levelsOf,
                                  ProfessorGoalRepository goalsOf,
                                  LanguageRepository languageCatalog,
                                  ProfessorRatingService ratings,
                                  SanctionService sanctions,
                                  ProfessorAvailabilityLookup availability) {
        this.profiles = profiles;
        this.languagesOf = languagesOf;
        this.levelsOf = levelsOf;
        this.goalsOf = goalsOf;
        this.languageCatalog = languageCatalog;
        this.ratings = ratings;
        this.sanctions = sanctions;
        this.availability = availability;
    }

    @Transactional(readOnly = true)
    public PagedProfessors search(ProfessorSearchCriteria criteria, ProfessorSortOption sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sortOf(sort));
        // Los sancionados con perfil oculto se resuelven aquí y entran ya como una lista de ids.
        ProfessorSearchCriteria effective = criteria.hiding(sanctions.hiddenProfessorIds());
        // Y lo mismo con la disponibilidad: la resuelve scheduling a través del puerto, para que
        // el buscador no tenga que saber qué es una regla de disponibilidad.
        if (criteria.pideHorasExactas()) {
            effective = effective.availableOnly(availability.professorsAvailableAt(criteria.days(), criteria.hours()));
        } else if (criteria.filtraPorDisponibilidad()) {
            effective = effective.availableOnly(availability.professorsAvailable(
                    criteria.days(), criteria.from(), criteria.to()));
        }
        Specification<ProfessorProfile> spec = ProfessorSpecifications.matching(effective);
        if (sort == ProfessorSortOption.RELEVANCE || sort == ProfessorSortOption.RATING) {
            spec = spec.and(ProfessorSpecifications.orderedBy(sort));
        }
        Page<ProfessorProfile> found = profiles.findAll(spec, pageable);

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
            // Los pone ProfessorSpecifications.orderedBy: dependen de las métricas de reputation,
            // que no son un atributo del perfil y no se pueden pedir con un Sort.
            case RELEVANCE, RATING -> Sort.unsorted();
        };
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 12;
        }
        return Math.min(size, 48);
    }
}
