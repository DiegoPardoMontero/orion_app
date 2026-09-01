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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.PasswordResetToken;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.PasswordResetTokenRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.UnprocessableException;

/**
 * Recuperación de contraseña por correo. Dos invariantes de seguridad:
 *  1. El endpoint de solicitud NUNCA revela si un correo existe (evita un oráculo de correos): con
 *     usuario o sin él, responde igual. La diferencia solo la nota quien tiene acceso al buzón.
 *  2. En la base vive el HASH del token, no el token. El secreto solo viaja en el correo. Un token
 *     es de un solo uso y caduca a los 30 minutos.
 */
@Service
public class PasswordResetService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder,
                                PasswordResetMailer mailer,
                                Clock clock,
                                @Value("${orion.app.base-url}") String baseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public void request(String email) {
        users.findByEmailIgnoreCase(email).filter(User::isActive).ifPresent(user -> {
            // Un nuevo enlace invalida los anteriores: solo el último debe funcionar.
            tokens.deleteByUserId(user.getId());

            String rawToken = randomToken();
            PasswordResetToken token = new PasswordResetToken(
                    user.getId(), sha256Hex(rawToken), clock.instant().plus(TTL));
            tokens.saveAndFlush(token);

            String link = baseUrl + "/restablecer?token=" + rawToken;
            mailer.sendResetLink(user.getEmail(), user.getFullName(), link);
        });
        // Sin usuario no se hace nada — pero se retorna igual: nadie sabe desde fuera si existía.
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHash(sha256Hex(rawToken))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new UnprocessableException("El enlace no es válido o ya expiró"));

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new UnprocessableException("El enlace no es válido o ya expiró"));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);

        token.markUsed(now); // de un solo uso: el mismo enlace no vuelve a servir
        tokens.save(token);
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
