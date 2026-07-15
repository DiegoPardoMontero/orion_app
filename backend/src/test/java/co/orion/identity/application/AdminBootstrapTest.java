package co.orion.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.config.ClockConfig;
import co.orion.shared.config.JpaAuditingConfig;

/**
 * AdminBootstrap se prueba a mano (no levantando el perfil prod entero): es un ApplicationRunner
 * y basta con darle un repositorio real y un encoder para ejercitar su lógica.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, ClockConfig.class})
class AdminBootstrapTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private UserRepository users;

    @Test
    void createsTheAdminWhenThereIsNone() throws Exception {
        new AdminBootstrap(users, passwordEncoder, "jefe@orion.co", "clave-fuerte").run(null);

        var admin = users.findByEmailIgnoreCase("jefe@orion.co");
        assertThat(admin).isPresent();
        assertThat(admin.get().getRole()).isEqualTo(UserRole.ADMIN);
        // Guardada con hash, no en claro.
        assertThat(passwordEncoder.matches("clave-fuerte", admin.get().getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() throws Exception {
        users.save(new User("ya@orion.co", passwordEncoder.encode("x"), "Admin Existente", UserRole.ADMIN));

        // Aunque lleguen variables distintas, no crea un segundo admin.
        new AdminBootstrap(users, passwordEncoder, "otro@orion.co", "otra-clave").run(null);

        assertThat(users.findByEmailIgnoreCase("otro@orion.co")).isEmpty();
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void failsFastWhenThereIsNoAdminAndNoVariables() {
        assertThatThrownBy(() -> new AdminBootstrap(users, passwordEncoder, "", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin admin no hay forma de entrar");
    }

    @Test
    void anExistingNonAdminUserDoesNotCountAsAnAdmin() throws Exception {
        // Un estudiante no es una puerta de entrada: el bootstrap debe crear el admin igual.
        users.save(new User("estu@orion.co", passwordEncoder.encode("x"), "Estudiante", UserRole.STUDENT));

        new AdminBootstrap(users, passwordEncoder, "jefe@orion.co", "clave-fuerte").run(null);

        assertThat(users.findByEmailIgnoreCase("jefe@orion.co")).isPresent();
    }
}
