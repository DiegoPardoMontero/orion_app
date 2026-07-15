package co.orion.identity.application;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

@Service
public class PasswordService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cambia la contraseña del usuario de la sesión. Se exige la actual: sin eso, cualquiera que
     * encontrara una sesión abierta podría dejar al dueño fuera de su propia cuenta.
     */
    @Transactional
    public void change(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnprocessableException("La contraseña actual no es correcta");
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
    }
}
