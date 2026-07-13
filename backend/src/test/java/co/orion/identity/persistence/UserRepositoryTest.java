package co.orion.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.shared.config.ClockConfig;
import co.orion.shared.config.JpaAuditingConfig;

/**
 * Postgres real vía Testcontainers, no H2: si el test pasa aquí, pasa en producción.
 * Un H2 en modo compatibilidad no tiene gen_random_uuid(), ni TIMESTAMPTZ, ni los CHECK
 * de la migración — probaríamos contra una base que no existe en ningún lado.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, ClockConfig.class})
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Test
    void findsAUserByEmailIgnoringCase() {
        users.save(new User("Ana@Orion.Local", "hash", "Ana Ramírez", UserRole.STUDENT));

        Optional<User> found = users.findByEmailIgnoreCase("ANA@ORION.LOCAL");

        assertThat(found).isPresent();
        // El constructor normaliza a minúsculas: esa es la única forma canónica que se guarda.
        assertThat(found.get().getEmail()).isEqualTo("ana@orion.local");
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void reportsWhetherAnEmailIsAlreadyTaken() {
        users.save(new User("juan@orion.local", "hash", "Juan Torres", UserRole.PROFESSOR));

        assertThat(users.existsByEmailIgnoreCase("JUAN@orion.local")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("nadie@orion.local")).isFalse();
    }

    @Test
    void rejectsADuplicateEmailWithTheUniqueConstraint() {
        users.saveAndFlush(new User("maria@orion.local", "hash", "María Gómez", UserRole.PROFESSOR));

        assertThatThrownBy(() -> users.saveAndFlush(
                new User("maria@orion.local", "otro-hash", "María Impostora", UserRole.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
