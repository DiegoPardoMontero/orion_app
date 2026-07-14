package co.orion.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ResourceNotFoundException;

@Service
public class AdminUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository users,
                            ProfessorProfileRepository profiles,
                            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> search(UserRole role, String query) {
        String q = (query == null || query.isBlank()) ? null : query.trim().toLowerCase();

        Specification<User> filtros = (root, criteriaQuery, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (role != null) {
                condiciones.add(cb.equal(root.get("role"), role));
            }
            if (q != null) {
                String patron = "%" + q + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), patron),
                        cb.like(cb.lower(root.get("email")), patron)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };

        return users.findAll(filtros, Sort.by("fullName"));
    }

    /**
     * Crea un usuario. Solo estudiantes y profesores: el admin no se fabrica desde el panel
     * (existe uno por configuración, y multiplicarlos es una decisión de negocio, no de UI).
     */
    @Transactional
    public User create(String email, String fullName, String whatsappPhone,
                       UserRole role, String rawPassword) {
        if (role != UserRole.STUDENT && role != UserRole.PROFESSOR) {
            throw new BusinessRuleViolationException("El rol debe ser STUDENT o PROFESSOR");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        // Chequeo amable; el árbitro final sigue siendo el UNIQUE de la base (ver abajo).
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }

        User user = new User(email, passwordEncoder.encode(rawPassword), fullName, role);
        user.changeWhatsappPhone(whatsappPhone);

        User saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }

        // Un profesor sin perfil no podría publicarse: nace con uno vacío, sin publicar.
        if (role == UserRole.PROFESSOR) {
            profiles.save(new ProfessorProfile(saved));
        }
        return saved;
    }

    /** Sin cambio de rol ni de email en el MVP: son decisiones con demasiadas consecuencias. */
    @Transactional
    public User update(UUID userId, String fullName, String whatsappPhone, String status) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (fullName != null && !fullName.isBlank()) {
            user.changeFullName(fullName.trim());
        }
        if (whatsappPhone != null) {
            user.changeWhatsappPhone(whatsappPhone.isBlank() ? null : whatsappPhone.trim());
        }
        if (status != null) {
            switch (parseStatus(status)) {
                case ACTIVE -> user.activate();
                case INACTIVE -> user.deactivate();
            }
        }
        return users.save(user);
    }

    private UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("status debe ser ACTIVE o INACTIVE");
        }
    }
}
