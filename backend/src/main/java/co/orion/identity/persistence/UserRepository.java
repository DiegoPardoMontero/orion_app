package co.orion.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;

/**
 * La búsqueda del panel usa Specification y no un @Query con "(:q is null or ...)": Postgres no
 * sabe tipar un parámetro nulo y acaba tratándolo como bytea ("function lower(bytea) does not
 * exist"). La Specification solo añade los filtros que llegaron, así que ese caso no existe.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    /** Los admins activos: a quienes hay que avisarles de algo que espera una decisión suya. */
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    List<User> findByRole(UserRole role);

    long countByRole(UserRole role);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(UserRole role);
}
