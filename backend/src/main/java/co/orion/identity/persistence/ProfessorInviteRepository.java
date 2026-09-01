package co.orion.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.ProfessorInvite;

public interface ProfessorInviteRepository extends JpaRepository<ProfessorInvite, UUID> {

    Optional<ProfessorInvite> findByTokenHash(String tokenHash);

    /** Reenviar una invitación invalida las anteriores del mismo profesor. */
    void deleteByUserId(UUID userId);
}
