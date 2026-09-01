package co.orion.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorInvite;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorInviteRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.PhoneNumbers;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.UnprocessableException;

/**
 * Alta de profesores por INVITACIÓN del admin (preserva la curaduría de academia, no marketplace).
 * El profesor nace INACTIVE con perfil vacío; al aceptar el enlace completa sus datos, fija su
 * contraseña y la cuenta pasa a ACTIVE. Mismo modelo de token que la recuperación de contraseña:
 * hash en la base, un solo uso, caducidad (7 días). Reenviar invalida el token anterior.
 */
@Service
public class ProfessorInviteService {

    private static final Duration TTL = Duration.ofDays(7);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final ProfessorInviteRepository invites;
    private final PasswordEncoder passwordEncoder;
    private final ProfessorInviteMailer mailer;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public ProfessorInviteService(UserRepository users,
                                  ProfessorProfileRepository profiles,
                                  ProfessorInviteRepository invites,
                                  PasswordEncoder passwordEncoder,
                                  ProfessorInviteMailer mailer,
                                  Clock clock,
                                  @Value("${orion.app.base-url}") String baseUrl) {
        this.users = users;
        this.profiles = profiles;
        this.invites = invites;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public void invite(String email) {
        User professor = users.findByEmailIgnoreCase(email).map(existing -> {
            // Solo se puede (re)invitar a un profesor que aún no aceptó (INACTIVE). Cualquier otro
            // usuario con ese correo —activo, estudiante o admin— es un conflicto.
            if (existing.getRole() != UserRole.PROFESSOR || existing.isActive()) {
                throw new ConflictException("Ya existe un usuario con ese correo");
            }
            return existing;
        }).orElseGet(() -> createPendingProfessor(email));

        invites.deleteByUserId(professor.getId());
        String rawToken = randomToken();
        invites.saveAndFlush(new ProfessorInvite(
                professor.getId(), sha256Hex(rawToken), clock.instant().plus(TTL)));

        mailer.sendInvite(professor.getEmail(), baseUrl + "/invitacion?token=" + rawToken);
    }

    /** Correo invitado (valida el token) para poder mostrarlo en la pantalla de invitación. */
    @Transactional(readOnly = true)
    public String invitedEmail(String rawToken) {
        return professorOf(usableInvite(rawToken)).getEmail();
    }

    @Transactional
    public User accept(String rawToken, String fullName, String password,
                       String whatsappPhone, String headline, String bio) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        ProfessorInvite invite = usableInvite(rawToken);
        User professor = professorOf(invite);

        professor.changeFullName(fullName.trim());
        professor.changePasswordHash(passwordEncoder.encode(password));
        professor.changeWhatsappPhone(PhoneNumbers.toE164(whatsappPhone));
        professor.activate();
        users.save(professor);

        ProfessorProfile profile = profiles.findByIdWithUser(professor.getId())
                .orElseGet(() -> new ProfessorProfile(professor));
        profile.describe(headline, bio); // sin publicar: publicarse es un paso aparte, como siempre
        profiles.save(profile);

        invite.markUsed(clock.instant()); // de un solo uso
        invites.save(invite);
        return professor;
    }

    private User createPendingProfessor(String email) {
        // Contraseña aleatoria imposible de adivinar: el profesor fija la suya real al aceptar.
        User professor = new User(email, passwordEncoder.encode(randomToken()),
                "Profesor invitado", UserRole.PROFESSOR);
        professor.deactivate();

        User saved;
        try {
            saved = users.saveAndFlush(professor);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
        profiles.save(new ProfessorProfile(saved));
        return saved;
    }

    private ProfessorInvite usableInvite(String rawToken) {
        return invites.findByTokenHash(sha256Hex(rawToken))
                .filter(invite -> invite.isUsable(clock.instant()))
                .orElseThrow(() -> new UnprocessableException("La invitación no es válida o ya expiró"));
    }

    private User professorOf(ProfessorInvite invite) {
        return users.findById(invite.getUserId())
                .orElseThrow(() -> new UnprocessableException("La invitación no es válida o ya expiró"));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
