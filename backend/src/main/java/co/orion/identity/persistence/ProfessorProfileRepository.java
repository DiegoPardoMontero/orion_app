package co.orion.identity.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.ProfessorProfile;

public interface ProfessorProfileRepository
        extends JpaRepository<ProfessorProfile, UUID>, JpaSpecificationExecutor<ProfessorProfile> {

    /**
     * "join fetch" y no solo "join": con open-in-view apagado, la sesión ya está cerrada cuando
     * el serializador toca profile.getUser(), así que el usuario tiene que venir en la consulta.
     * Publicado NO basta: si el usuario fue desactivado, tampoco debe aparecer en el directorio.
     */
    @Query("""
            select p from ProfessorProfile p
            join fetch p.user u
            where p.published = true
              and u.status = co.orion.identity.domain.UserStatus.ACTIVE
            order by u.fullName
            """)
    List<ProfessorProfile> findPublished();

    @Query("""
            select p from ProfessorProfile p
            join fetch p.user u
            where p.published = true
              and u.status = co.orion.identity.domain.UserStatus.ACTIVE
              and u.id = :professorId
            """)
    Optional<ProfessorProfile> findPublishedById(@Param("professorId") UUID professorId);

    /** Con el usuario ya cargado: quien reciba la entidad la usará fuera de la transacción. */
    @Query("""
            select p from ProfessorProfile p
            join fetch p.user u
            where u.id = :professorId
            """)
    Optional<ProfessorProfile> findByIdWithUser(@Param("professorId") UUID professorId);

    /** Cuántos profesores están publicados en el marketplace. */
    long countByPublishedTrue();

    boolean existsByPublicSlug(String publicSlug);

    /** El profesor de un enlace para invitar, si está publicado. */
    @Query("select p from ProfessorProfile p where p.publicSlug = :slug and p.published = true")
    Optional<ProfessorProfile> findPublishedBySlug(@Param("slug") String slug);

    /**
     * Arranca el conteo del profe fundador con su primera clase pagada. Solo lo hace si es fundador y
     * el conteo no ha empezado: la base decide, así que dos pagos aprobados a la vez no lo arrancan
     * dos veces y un pago cancelado después no lo reinicia. Devuelve 1 si lo arrancó.
     */
    // Sin clearAutomatically: se llama en medio de la reserva o del webhook, y vaciar el contexto
    // dejaría desconectadas la reserva y el pago que siguen en uso en la misma transacción.
    @Modifying
    @Query(value = """
            update professor_profiles
               set founder_started_at = :startedAt, founder_until = :until
             where user_id = :professorId
               and founder_rate_bps is not null
               and founder_started_at is null
            """, nativeQuery = true)
    int startFounderClock(@Param("professorId") UUID professorId,
                          @Param("startedAt") Instant startedAt,
                          @Param("until") Instant until);

    /** Quita el beneficio, con su conteo. Las reservas que ya existen conservan su comisión. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update professor_profiles
               set founder_rate_bps = null, founder_period_months = null, founder_granted_at = null,
                   founder_started_at = null, founder_until = null, founder_expiry_notified_at = null
             where user_id = :professorId
            """, nativeQuery = true)
    int revokeFounder(@Param("professorId") UUID professorId);
}
