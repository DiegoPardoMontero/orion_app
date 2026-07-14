package co.orion.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.ProfessorProfile;

public interface ProfessorProfileRepository extends JpaRepository<ProfessorProfile, UUID> {

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
}
