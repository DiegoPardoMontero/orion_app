package co.orion.legal.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.legal.domain.AgreementAcceptance;

public interface AgreementAcceptanceRepository extends JpaRepository<AgreementAcceptance, UUID> {

    boolean existsByUserIdAndDocumentCode(UUID userId, String documentCode);

    /**
     * Por versión, no solo por documento: publicar unos Términos nuevos tiene que volver a pedir
     * la aceptación. Preguntar solo por el código daría por aceptada la versión 2.0 a quien firmó
     * la 1.0.
     */
    boolean existsByUserIdAndDocumentCodeAndVersion(UUID userId, String documentCode, String version);
}
