package co.orion.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.ProfessorInvite;

public interface ProfessorInviteRepository extends JpaRepository<ProfessorInvite, UUID> {

    Optional<ProfessorInvite> findByTokenHash(String tokenHash);

    /** Reenviar una invitación invalida las anteriores sin usar del mismo correo. */
    @Modifying
    @Query("delete from ProfessorInvite i where i.email = :email and i.usedAt is null")
    void deleteUnusedByEmail(@Param("email") String email);

    /** Si la cuenta nació de una invitación con el beneficio de fundador. */
    boolean existsByUserIdAndFounderTrue(UUID userId);
}
