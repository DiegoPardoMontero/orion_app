package co.orion.catalog.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.domain.Language;
import co.orion.catalog.domain.TeachingGoal;
import co.orion.catalog.persistence.LanguageRepository;
import co.orion.catalog.persistence.TeachingGoalRepository;

/** Catálogo de idiomas y objetivos activos, ordenados para pintar filtros y formularios. */
@Service
public class CatalogService {

    private final LanguageRepository languages;
    private final TeachingGoalRepository goals;

    public CatalogService(LanguageRepository languages, TeachingGoalRepository goals) {
        this.languages = languages;
        this.goals = goals;
    }

    @Transactional(readOnly = true)
    public List<Language> activeLanguages() {
        return languages.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<TeachingGoal> activeGoals() {
        return goals.findByActiveTrueOrderByDisplayOrderAsc();
    }
}
