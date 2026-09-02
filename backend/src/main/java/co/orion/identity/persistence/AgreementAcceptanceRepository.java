package co.orion.identity.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.AgreementAcceptance;

public interface AgreementAcceptanceRepository extends JpaRepository<AgreementAcceptance, UUID> {

    boolean existsByUserIdAndDocumentCode(UUID userId, String documentCode);
}
