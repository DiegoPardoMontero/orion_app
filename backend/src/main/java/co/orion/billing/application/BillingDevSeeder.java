package co.orion.billing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import co.orion.billing.domain.BreBKeyType;
import co.orion.billing.domain.CreditReason;
import co.orion.billing.domain.IdDocumentType;
import co.orion.billing.domain.PayoutDestination;
import co.orion.billing.domain.ProfessorPayoutDetails;
import co.orion.billing.domain.StudentCredit;
import co.orion.billing.persistence.ProfessorPayoutDetailsRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;

/**
 * Saldo de desarrollo para la estudiante de prueba, y los datos de pago de los profes sembrados.
 *
 * Existe por una razón concreta: sin pasarela de por medio no hay forma de llegar a una clase
 * CONFIRMED en local, y media aplicación (la sala virtual, la asistencia, las reseñas, la
 * cancelación) vive del otro lado de esa confirmación. Un crédito que cubre varias clases abre ese
 * camino usando una ruta real del producto —el saldo a favor— en vez de una pasarela falsa.
 *
 * Solo en el perfil {@code local}, y solo si la estudiante todavía no tiene saldo: idempotente,
 * como el resto de la semilla.
 */
@Component
@Profile("local")
@Order(100)   // después de DevDataSeeder: necesita que la estudiante ya exista
public class BillingDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingDevSeeder.class);

    private static final String STUDENT_EMAIL = "ana@orion.local";
    private static final long DEV_CREDIT_COP = 500_000;

    private final UserRepository users;
    private final StudentCreditRepository credits;
    private final ProfessorPayoutDetailsRepository payoutDetails;

    public BillingDevSeeder(UserRepository users, StudentCreditRepository credits,
                            ProfessorPayoutDetailsRepository payoutDetails) {
        this.users = users;
        this.credits = credits;
        this.payoutDetails = payoutDetails;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.findByEmailIgnoreCase(STUDENT_EMAIL)
                .filter(user -> user.getRole() == UserRole.STUDENT)
                .filter(user -> credits.findUsable(user.getId(), Instant.now()).isEmpty())
                .ifPresent(student -> {
                    credits.save(new StudentCredit(student.getId(), DEV_CREDIT_COP,
                            CreditReason.ADMIN_ADJUSTMENT, null, null, null));
                    log.info("Semilla: saldo de desarrollo de {} COP para {}",
                            DEV_CREDIT_COP, STUDENT_EMAIL);
                });
        seedPayoutDetails("maria@orion.local", "3009876543", "1020304050", "María Gómez");
        seedPayoutDetails("juan@orion.local", "3005556677", "1030405060", "Juan Torres");
    }

    /**
     * Los datos de pago son obligatorios para el profe aprobado (Pardo, 26/09/2026): sin ellos la app
     * no lo deja seguir al entrar, y taparía las pruebas. Directo al repositorio y no por el servicio,
     * que le mandaría a la semilla un correo de «cambiaron tus datos de pago» en cada arranque limpio.
     */
    private void seedPayoutDetails(String email, String celular, String cedula, String titular) {
        users.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() == UserRole.PROFESSOR)
                .filter(user -> !payoutDetails.existsById(user.getId()))
                .ifPresent(profe -> {
                    payoutDetails.save(new ProfessorPayoutDetails(profe.getId(), PayoutDestination.of(
                            BreBKeyType.PHONE, celular, IdDocumentType.CC, cedula, titular), Instant.now()));
                    log.info("Semilla: datos de pago de {}", email);
                });
    }
}
