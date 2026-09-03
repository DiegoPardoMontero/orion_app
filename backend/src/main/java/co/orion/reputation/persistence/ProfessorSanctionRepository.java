package co.orion.reputation.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.reputation.domain.ProfessorSanction;
import co.orion.reputation.domain.SanctionState;

public interface ProfessorSanctionRepository extends JpaRepository<ProfessorSanction, UUID> {

    List<ProfessorSanction> findByProfessorIdOrderByCreatedAtDesc(UUID professorId);

    List<ProfessorSanction> findByStateOrderByCreatedAtDesc(SanctionState state);

    /** Las que surten efecto ahora: activas, ya empezadas y sin vencer. */
    @Query("""
            select s from ProfessorSanction s
            where s.professorId = :professorId
              and s.state = co.orion.reputation.domain.SanctionState.ACTIVE
              and s.startsAt <= :now
              and (s.endsAt is null or s.endsAt > :now)
            """)
    List<ProfessorSanction> findActive(@Param("professorId") UUID professorId, @Param("now") Instant now);

    /** Los profesores con alguna sanción activa que los saca del buscador. */
    @Query("""
            select distinct s.professorId from ProfessorSanction s
            where s.state = co.orion.reputation.domain.SanctionState.ACTIVE
              and s.startsAt <= :now
              and (s.endsAt is null or s.endsAt > :now)
              and s.type in (co.orion.reputation.domain.SanctionType.PROFILE_HIDDEN,
                             co.orion.reputation.domain.SanctionType.ACCOUNT_SUSPENDED)
            """)
    List<UUID> findHiddenProfessorIds(@Param("now") Instant now);

    /** Los que no pueden recibir reservas nuevas. Las ya confirmadas se respetan siempre. */
    @Query("""
            select distinct s.professorId from ProfessorSanction s
            where s.state = co.orion.reputation.domain.SanctionState.ACTIVE
              and s.startsAt <= :now
              and (s.endsAt is null or s.endsAt > :now)
              and s.type in (co.orion.reputation.domain.SanctionType.BOOKINGS_SUSPENDED,
                             co.orion.reputation.domain.SanctionType.PROFILE_HIDDEN,
                             co.orion.reputation.domain.SanctionType.ACCOUNT_SUSPENDED)
            """)
    List<UUID> findBookingBlockedProfessorIds(@Param("now") Instant now);
}
