package co.orion.identity.application;

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

/** Buscador del marketplace: filtra, pagina y ensambla las tarjetas resolviendo idiomas por lotes. */
@Service
public class ProfessorSearchService {

    private final ProfessorProfileRepository profiles;
    private final ProfessorLanguageRepository languagesOf;
    private final ProfessorLanguageLevelRepository levelsOf;
    private final ProfessorGoalRepository goalsOf;
    private final LanguageRepository languageCatalog;

    public ProfessorSearchService(ProfessorProfileRepository profiles,
                                  ProfessorLanguageRepository languagesOf,
                                  ProfessorLanguageLevelRepository levelsOf,
                                  ProfessorGoalRepository goalsOf,
                                  LanguageRepository languageCatalog) {
        this.profiles = profiles;
        this.languagesOf = languagesOf;
        this.levelsOf = levelsOf;
        this.goalsOf = goalsOf;
        this.languageCatalog = languageCatalog;
    }

    @Transactional(readOnly = true)
    public PagedProfessors search(ProfessorSearchCriteria criteria, ProfessorSortOption sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sortOf(sort));
        Page<ProfessorProfile> found = profiles.findAll(ProfessorSpecifications.matching(criteria), pageable);

        List<UUID> ids = found.getContent().stream().map(ProfessorProfile::getUserId).toList();
        Map<UUID, List<ProfessorLanguage>> langs = ids.isEmpty() ? Map.of()
                : languagesOf.findByProfessorIdIn(ids).stream().collect(Collectors.groupingBy(ProfessorLanguage::getProfessorId));
        Map<UUID, List<ProfessorLanguageLevel>> levels = ids.isEmpty() ? Map.of()
                : levelsOf.findByProfessorIdIn(ids).stream().collect(Collectors.groupingBy(ProfessorLanguageLevel::getProfessorId));
        Map<UUID, List<ProfessorGoal>> goals = ids.isEmpty() ? Map.of()
                : goalsOf.findByProfessorIdIn(ids).stream().collect(Collectors.groupingBy(ProfessorGoal::getProfessorId));
        Map<String, Language> catalog = languageCatalog.findAll().stream()
                .collect(Collectors.toMap(Language::getCode, l -> l));

        List<ProfessorCard> cards = found.getContent().stream()
                .map(p -> toCard(p, langs, levels, goals, catalog))
                .toList();

        return new PagedProfessors(cards, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    private ProfessorCard toCard(ProfessorProfile p,
                                 Map<UUID, List<ProfessorLanguage>> langs,
                                 Map<UUID, List<ProfessorLanguageLevel>> levels,
                                 Map<UUID, List<ProfessorGoal>> goals,
                                 Map<String, Language> catalog) {
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

        return new ProfessorCard(
                p.getUserId(),
                p.getUser().getFullName(),
                p.getUser().getPhotoUrl(),
                p.getHeadline(),
                p.getCity(),
                p.getCountryCode(),
                p.isCertified(),
                p.getHourlyRateCop(),
                badges,
                levelCodes,
                goalCodes);
    }

    private Sort sortOf(ProfessorSortOption sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("hourlyRateCop"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("hourlyRateCop"));
            // RATING aún no existe (Bloque 6): cae al orden estable de RELEVANCE.
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
