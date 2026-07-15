package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;

/**
 * En producción no hay semilla (@Profile("local")). El admin —la única puerta de entrada— lo
 * crea esto, desde variables de entorno, en el primer arranque.
 *
 * Fail fast: si no existe ningún admin y las variables faltan, la aplicación NO arranca. Es
 * deliberado — un deploy caído que grita el problema es mejor que una plataforma en pie a la que
 * nadie puede entrar y que parece sana.
 */
@Component
@Profile("prod")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(UserRepository users,
                          PasswordEncoder passwordEncoder,
                          @Value("${orion.admin.email:}") String adminEmail,
                          @Value("${orion.admin.password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Idempotente: con un admin ya creado no se toca nada, aunque cambien las variables.
        if (users.existsByRole(UserRole.ADMIN)) {
            log.info("Ya existe un administrador; AdminBootstrap no hace nada.");
            return;
        }

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "No hay ningún administrador y faltan ORION_ADMIN_EMAIL / ORION_ADMIN_PASSWORD. "
                            + "Defínelas: sin admin no hay forma de entrar a la plataforma.");
        }

        User admin = new User(
                adminEmail, passwordEncoder.encode(adminPassword), "Orion Admin", UserRole.ADMIN);
        users.save(admin);
        log.info("AdminBootstrap: administrador de producción creado ({}).", admin.getEmail());
    }
}
