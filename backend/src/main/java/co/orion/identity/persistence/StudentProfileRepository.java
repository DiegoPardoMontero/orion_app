package co.orion.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import co.orion.identity.domain.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    /** Con el usuario ya cargado: la vista del perfil necesita su nombre y su foto. */
    @Query("select p from StudentProfile p join fetch p.user where p.userId = :userId")
    Optional<StudentProfile> findByIdWithUser(UUID userId);
}
