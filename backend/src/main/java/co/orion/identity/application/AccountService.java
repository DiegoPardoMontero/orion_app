package co.orion.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * La cuenta que gestiona el propio usuario: su nombre y su WhatsApp. Distinto del alta del admin
 * ({@link AdminUserService}, que también toca rol y estado) y del perfil público del profesor
 * ({@link ProfessorProfileService}). Aquí nadie cambia su rol, su email ni su estado.
 */
@Service
public class AccountService {

    private final UserRepository users;

    public AccountService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Transactional
    public User update(UUID userId, String fullName, String whatsappPhone) {
        User user = get(userId);
        if (fullName != null && !fullName.isBlank()) {
            user.changeFullName(fullName.trim());
        }
        // El WhatsApp es opcional: vacío lo borra (el usuario decidió no dejarlo).
        String phone = (whatsappPhone == null || whatsappPhone.isBlank()) ? null : whatsappPhone.trim();
        user.changeWhatsappPhone(phone);
        return users.save(user);
    }
}
