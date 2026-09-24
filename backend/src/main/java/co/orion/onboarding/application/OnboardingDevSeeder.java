package co.orion.onboarding.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * En local, las cuentas de la semilla ya vieron la bienvenida. La suite de humo entra con ellas en
 * cada prueba, y un recorrido que se abre solo tapa la pantalla que la prueba va a mirar. Las
 * cuentas nuevas —las que se registran en la prueba o a mano— sí lo ven, y cualquiera lo reabre
 * desde Ayuda.
 */
@Component
@Profile("local")
@Order(100)   // después de DevDataSeeder: necesita que las cuentas ya existan
public class OnboardingDevSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public OnboardingDevSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.update("""
                insert into onboarding_steps (user_id, step)
                select u.id, s.step
                  from users u
                  join (values ('PROFESSOR', 'WELCOME_VIDEO'), ('PROFESSOR', 'TOUR_PROFESSOR'),
                               ('STUDENT', 'TOUR_STUDENT')) as s(role, step) on s.role = u.role
                 where u.email like '%@orion.local'
                on conflict (user_id, step) do nothing
                """);
    }
}
