package co.orion.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.AdolescentProfile;

public interface AdolescentProfileRepository extends JpaRepository<AdolescentProfile, UUID> {

    List<AdolescentProfile> findByGuardianUserIdAndClosedAtIsNullOrderByCreatedAtAsc(UUID guardianUserId);

    List<AdolescentProfile> findByGuardianUserIdOrderByCreatedAtAsc(UUID guardianUserId);

    Optional<AdolescentProfile> findByIdAndGuardianUserId(UUID id, UUID guardianUserId);

    long countByGuardianUserIdAndClosedAtIsNull(UUID guardianUserId);
}
