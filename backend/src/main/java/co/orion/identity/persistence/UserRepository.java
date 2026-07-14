package co.orion.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import co.orion.identity.domain.User;

/**
 * La búsqueda del panel usa Specification y no un @Query con "(:q is null or ...)": Postgres no
 * sabe tipar un parámetro nulo y acaba tratándolo como bytea ("function lower(bytea) does not
 * exist"). La Specification solo añade los filtros que llegaron, así que ese caso no existe.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

}
