package co.orion.identity.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
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
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String fullName, String email, String rawPassword, String whatsappPhone) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        // Chequeo amable con buen mensaje; el árbitro final es el UNIQUE de la base (ver abajo).
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Ya existe una cuenta con ese correo");
        }

        User user = new User(email, passwordEncoder.encode(rawPassword), fullName, UserRole.STUDENT);
        if (whatsappPhone != null && !whatsappPhone.isBlank()) {
            user.changeWhatsappPhone(whatsappPhone.trim());
        }

        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Cierra la ventana entre el chequeo y el INSERT: dos altas del mismo correo a la vez.
            throw new ConflictException("Ya existe una cuenta con ese correo");
        }
    }
}
