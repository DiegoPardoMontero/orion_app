package co.orion.assessment.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.assessment.domain.AssessmentLead;

public interface AssessmentLeadRepository extends JpaRepository<AssessmentLead, UUID> {

    Optional<AssessmentLead> findByTokenHash(String tokenHash);

    /** Los que nadie reclamó a tiempo. El borrado arrastra sus diagnósticos por la FK en cascada. */
    @Modifying
    @Query("delete from AssessmentLead l where l.claimedAt is null and l.createdAt < :limite")
    int deleteUnclaimedBefore(@Param("limite") Instant limite);
}
