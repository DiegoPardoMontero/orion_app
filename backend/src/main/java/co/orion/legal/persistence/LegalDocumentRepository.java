package co.orion.legal.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.legal.domain.LegalDocument;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findByCodeAndVersion(String code, String version);

    /**
     * La versión vigente hoy: la de mayor {@code effective_from} que ya haya entrado en vigor. Una
     * versión con fecha futura existe pero todavía no rige — así se puede publicar un cambio con
     * antelación sin que empiece a aplicarse solo.
     */
    @Query("""
            select d from LegalDocument d
            where d.code = :code and d.effectiveFrom <= :today
            order by d.effectiveFrom desc
            limit 1
            """)
    Optional<LegalDocument> findVigente(@Param("code") String code, @Param("today") LocalDate today);

    List<LegalDocument> findByCodeOrderByEffectiveFromDesc(String code);
}
