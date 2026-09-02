package co.orion.catalog.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.catalog.domain.Language;

public interface LanguageRepository extends JpaRepository<Language, String> {

    List<Language> findByActiveTrueOrderByDisplayOrderAsc();
}
