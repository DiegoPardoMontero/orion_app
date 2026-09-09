package co.orion.identity.application;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.PhoneNumbers;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;

/**
 * Auto-registro de estudiantes. Distinto del alta del admin ({@link AdminUserService}) a propósito:
 * este flujo es público, siempre crea STUDENT y nunca fabrica perfiles de profesor. Mantenerlo
 * aparte deja que evolucione solo (p. ej. verificación de correo) sin tocar el panel del admin.
 */
@Service
public class RegistrationService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final StudentProfileService studentProfiles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(UserRepository users,
                               StudentProfileService studentProfiles,
                               PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User register(String fullName, String email, String rawPassword, String whatsappPhone) {
        return register(fullName, email, rawPassword, whatsappPhone, false, true);
    }

    @Transactional
    public User register(String fullName, String email, String rawPassword, String whatsappPhone,
                         boolean wantsToTeach, boolean adult) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        // Orión no acepta menores de edad. El art. 7 de la Ley 1581 de 2012 prohíbe tratar datos
        // de niños, niñas y adolescentes salvo con autorización del representante legal (art. 12
        // del Decreto 1377 de 2013), y ese flujo no existe aquí. La puerta se cierra en el alta,
        // que es el único sitio donde cerrarla no deja a nadie a medias.
        if (!adult) {
            throw new BusinessRuleViolationException(
                    "Orión está disponible solo para mayores de 18 años.");
        }
        // Chequeo amable con buen mensaje; el árbitro final es el UNIQUE de la base (ver abajo).
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Ya existe una cuenta con ese correo");
        }

        User user = new User(email, passwordEncoder.encode(rawPassword), fullName, UserRole.STUDENT);
        user.changeWhatsappPhone(PhoneNumbers.toE164(whatsappPhone));
        user.confirmAdulthood(clock.instant());
        if (wantsToTeach) {
            user.intendsToTeach();
        }

        try {
            User creado = users.saveAndFlush(user);
            // La ficha nace con la cuenta: así ningún código tiene que manejar el caso
            // "estudiante sin ficha", y el perfil nace privado, que es lo que debe ser. También la
            // del aspirante: si mañana su postulación se rechaza, la cuenta sigue siendo una cuenta
            // de estudiante completa, sin nada que reparar.
            studentProfiles.createFor(creado);
            return creado;
        } catch (DataIntegrityViolationException ex) {
            // Cierra la ventana entre el chequeo y el INSERT: dos altas del mismo correo a la vez.
            throw new ConflictException("Ya existe una cuenta con ese correo");
        }
    }
}
