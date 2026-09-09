package co.orion.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.EmailVerificationToken;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.EmailVerificationTokenRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.UnprocessableException;

/**
 * Verificación de la dirección de correo.
 *
 * <p>Sin esto, cualquiera se registra con el correo de otra persona y esa persona recibe
 * confirmaciones de clases que no reservó, con su hora y el nombre de su profesor. Y al revés: una
 * reserva confirmada contra un buzón que no existe es una clase que nadie va a recordar.
 *
 * <p><strong>Qué se bloquea sin verificar: reservar, y nada más.</strong> Se puede entrar, buscar
 * profesores, mirar perfiles y escribir a soporte. Cerrar la puerta antes obligaría a verificar
 * para poder mirar, que es la forma más rápida de perder a alguien que solo quería ver precios.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /** 24 horas y no 30 minutos como el de contraseña: aquí nadie está esperando delante. */
    private static final Duration TTL = Duration.ofHours(24);

    /** El freno al reenvío. Un botón que manda correos es un altavoz si no se limita. */
    private static final Duration VENTANA_REENVIO = Duration.ofHours(1);
    private static final int MAX_ENVIOS_POR_VENTANA = 3;

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final EmailVerificationMailer mailer;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository users,
                                    EmailVerificationTokenRepository tokens,
                                    EmailVerificationMailer mailer,
                                    Clock clock,
                                    @Value("${orion.app.base-url}") String baseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /**
     * Emite un token y manda el correo. Se llama al registrarse y al pedir el reenvío.
     *
     * <p>El fallo de envío NO tumba la operación que la disparó: el registro ya funcionó y la
     * cuenta existe. Si el correo no sale, la persona pulsa «Reenviar» — pero que no se le
     * deshaga la cuenta por un SMTP caído.
     */
    @Transactional
    public void send(UUID userId) {
        User user = users.findById(userId).orElseThrow();
        if (user.isEmailVerified()) {
            return;
        }

        Instant now = clock.instant();
        long recientes = tokens.countByUserIdAndCreatedAtAfter(userId, now.minus(VENTANA_REENVIO));
        if (recientes >= MAX_ENVIOS_POR_VENTANA) {
            throw new BusinessRuleViolationException(
                    "Ya te enviamos varios correos de verificación. Revisa tu bandeja y la carpeta "
                            + "de spam; puedes volver a intentarlo en una hora.");
        }

        // Un enlace nuevo invalida los anteriores: solo el último debe funcionar. Se marcan
        // como usados en vez de borrarse — borrarlos dejaría al contador de arriba sin nada
        // que contar, y el freno no frenaría.
        tokens.invalidateAllFor(userId, now);

        String rawToken = randomToken();
        tokens.saveAndFlush(new EmailVerificationToken(
                userId, sha256Hex(rawToken), user.getEmail(), now.plus(TTL)));

        try {
            mailer.sendVerificationLink(user.getEmail(), user.getFullName(),
                    baseUrl + "/verificar?token=" + rawToken);
        } catch (RuntimeException ex) {
            log.error("No se pudo enviar la verificación a {}: {}", user.getEmail(), ex.toString());
        }
    }

    /**
     * Consume el token del enlace. Idempotente hacia el buen final: si el correo ya estaba
     * verificado, no es un error — es alguien que pulsó dos veces.
     */
    @Transactional
    public void verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokens.findByTokenHash(sha256Hex(rawToken))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new UnprocessableException(
                        "El enlace no es válido o ya expiró. Pide uno nuevo desde tu cuenta."));

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new UnprocessableException("El enlace no es válido o ya expiró"));

        // El correo pudo cambiar entre el envío y el clic. Verificar entonces marcaría como
        // comprobada una dirección que la cuenta ya no usa.
        if (!user.getEmail().equalsIgnoreCase(token.getEmail())) {
            throw new UnprocessableException(
                    "Ese enlace era para otra dirección de correo. Pide uno nuevo desde tu cuenta.");
        }

        user.markEmailVerified(now);
        users.save(user);

        token.markUsed(now);
        tokens.save(token);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
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
