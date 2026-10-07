package co.orion.identity.application;

import java.time.Clock;
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

import co.orion.identity.domain.ApplicationEventType;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.TeacherApplicationEvent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationEventRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.PhoneNumbers;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ResourceNotFoundException;

@Service
public class AdminUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final TeacherApplicationRepository applications;
    private final TeacherApplicationEventRepository applicationEvents;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminUserService(UserRepository users,
                            ProfessorProfileRepository profiles,
                            TeacherApplicationRepository applications,
                            TeacherApplicationEventRepository applicationEvents,
                            PasswordEncoder passwordEncoder,
                            Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.applications = applications;
        this.applicationEvents = applicationEvents;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
    public User create(UUID adminId, String email, String fullName, String whatsappPhone,
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
        user.changeWhatsappPhone(PhoneNumbers.toE164(whatsappPhone));

        User saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }

        // Un profesor sin perfil no podría publicarse: nace con uno vacío, sin publicar. Y con su
        // postulación aprobada por quien lo crea: sin una APPROVED, publicar, mostrar cupos y salir en
        // el buscador responden 403, y el profe que el admin daba de alta quedaba sin salida.
        if (role == UserRole.PROFESSOR) {
            profiles.save(new ProfessorProfile(saved));
            TeacherApplication aprobada = applications.saveAndFlush(new TeacherApplication(
                    saved.getId(), ApplicationStatus.APPROVED, adminId, clock.instant()));
            applicationEvents.save(new TeacherApplicationEvent(
                    aprobada.getId(), ApplicationEventType.APPROVED, adminId, "Creado por el admin desde Usuarios"));
        }
        return saved;
    }

    /** Sin cambio de rol ni de email en el MVP: son decisiones con demasiadas consecuencias. */
    @Transactional
    public User update(UUID actorId, UUID userId, String fullName, String whatsappPhone, String status) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Desactivar al admin que opera, o al último admin activo, dejaba Orión sin nadie que pudiera
        // entrar al panel: la sesión moría en la siguiente petición, el login respondía «cuenta
        // inactiva» y AdminBootstrap no hacía nada porque el admin seguía existiendo. Solo un UPDATE
        // a mano en Postgres lo arreglaba (revisión del 06/10/2026). La purga ya se negaba; esto no.
        if (status != null && parseStatus(status) == UserStatus.INACTIVE) {
            if (userId.equals(actorId)) {
                throw new BusinessRuleViolationException("No puedes desactivar tu propia cuenta.");
            }
            if (user.getRole() == UserRole.ADMIN && user.isActive()
                    && users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
                throw new BusinessRuleViolationException(
                        "Es el único administrador activo: desactivarlo dejaría Orión sin acceso al panel.");
            }
        }

        if (fullName != null && !fullName.isBlank()) {
            user.changeFullName(fullName.trim());
        }
        if (whatsappPhone != null) {
            user.changeWhatsappPhone(PhoneNumbers.toE164(whatsappPhone));
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
