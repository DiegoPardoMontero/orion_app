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

    /**
     * Lo reclama si nadie lo ha hecho: 1 si esta llamada lo ganó, 0 si otra se adelantó. Al crear la
     * cuenta el navegador lanza varias peticiones a la vez con la misma cookie, y cada una intentaba
     * la mudanza; la fila bloqueada hace esperar a las demás, que al seguir ya lo ven reclamado.
     */
    @Modifying
    @Query("""
            update AssessmentLead l set l.claimedBy = :cuenta, l.claimedAt = :ahora
             where l.id = :id and l.claimedAt is null
            """)
    int claimIfUnclaimed(@Param("id") UUID id, @Param("cuenta") UUID cuenta, @Param("ahora") Instant ahora);

    /** Los que nadie reclamó a tiempo. El borrado arrastra sus diagnósticos por la FK en cascada. */
    @Modifying
    @Query("delete from AssessmentLead l where l.claimedAt is null and l.createdAt < :limite")
    int deleteUnclaimedBefore(@Param("limite") Instant limite);
}
