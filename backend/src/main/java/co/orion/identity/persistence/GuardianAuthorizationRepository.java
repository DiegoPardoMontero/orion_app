package co.orion.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.GuardianAuthorization;

public interface GuardianAuthorizationRepository extends JpaRepository<GuardianAuthorization, UUID> {

    /** La autorización viva del perfil, si la hay: sin ella no se reserva. */
    Optional<GuardianAuthorization> findByAdolescentProfileIdAndRevokedAtIsNull(UUID adolescentProfileId);

    List<GuardianAuthorization> findByGuardianUserId(UUID guardianUserId);
}
