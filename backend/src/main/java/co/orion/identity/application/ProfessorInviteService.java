package co.orion.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.api.InvitationView;
import co.orion.identity.domain.ProfessorInvite;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorInviteRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.time.BusinessZone;

/**
 * Invitaciones de profesores (decisión de Pardo del 25/09/2026, brief del profe fundador).
 *
 * <p>La invitación ya no crea la cuenta: guarda el correo, el nombre con el que se saluda al profe,
 * quién lo invita y si trae el beneficio de fundador. El invitado abre el enlace, ve la pantalla de
 * invitación y crea su cuenta en el registro de profesor, con ese correo y sin poder cambiarlo. Ahí
 * acepta los Términos y la política de datos, lleva su postulación y Sofía la aprueba como la de
 * cualquier aspirante. El beneficio de fundador se otorga al aprobarse.
 *
 * <p>Mismo modelo de token que la recuperación de contraseña: hash en la base, un solo uso, 7 días.
 * Reenviar invalida la anterior sin usar del mismo correo. Desde el 29/09/2026 el correo es opcional:
 * la invitación puede salir por WhatsApp, con el enlace que devuelve {@link #invite}.
 */
@Service
public class ProfessorInviteService {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String BASE_RATE_KEY = "commission_rate_bps";

    private final UserRepository users;
    private final ProfessorInviteRepository invites;
    private final PlatformSettingsService settings;
    private final ProfessorInviteMailer mailer;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public ProfessorInviteService(UserRepository users,
                                  ProfessorInviteRepository invites,
                                  PlatformSettingsService settings,
                                  ProfessorInviteMailer mailer,
                                  Clock clock,
                                  @Value("${orion.app.base-url}") String baseUrl) {
        this.users = users;
        this.invites = invites;
        this.settings = settings;
        this.mailer = mailer;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /** El enlace recién creado: la respuesta al admin es el único sitio donde existe el token en claro. */
    public record InviteLink(String url, Instant expiresAt, boolean emailed) {
    }

    /**
     * El admin invita a un profe. Con correo, le llega el enlace ahí; si ya hay una cuenta con ese
     * correo no se invita (si es un profe, el beneficio de fundador se le otorga desde Usuarios). Sin
     * correo (Pardo, 29/09/2026: «no de todos los profes tengo el correo»), no se manda nada: el admin
     * copia el enlace o lo manda por WhatsApp. En los dos casos el enlace es de una sola persona.
     *
     * <p>Quién invita se guarda ({@code invited_by}) para la trazabilidad, pero no se le muestra al
     * profe: ni en el correo ni en la pantalla de la invitación sale el nombre ni el cargo de nadie,
     * la invitación es de Orión (Pardo, 26/09/2026). Por eso el cargo que antes se pedía ya no se guarda.
     */
    @Transactional
    public InviteLink invite(UUID adminId, String email, String professorName, boolean founder) {
        boolean conCorreo = email != null && !email.isBlank();
        boolean conNombre = professorName != null && !professorName.isBlank();
        if (!conCorreo && !conNombre) {
            // Sin los dos, en Usuarios no habría forma de saber a quién se le mandó cada enlace.
            throw new UnprocessableException("Escribe al menos el nombre del profe, o su correo.");
        }
        String correo = conCorreo ? email.trim().toLowerCase(Locale.ROOT) : null;
        if (conCorreo) {
            if (users.existsByEmailIgnoreCase(correo)) {
                throw new ConflictException(
                        "Ya existe una cuenta con ese correo. Si es un profe, dale el beneficio de fundador desde Usuarios.");
            }
            invites.deleteUnusedByEmail(correo);
        }

        String rawToken = randomToken();
        ProfessorInvite invite = invites.saveAndFlush(new ProfessorInvite(correo, professorName, adminId, founder,
                sha256Hex(rawToken), clock.instant().plus(TTL)));
        String url = baseUrl + "/invitacion/" + rawToken;
        if (conCorreo) {
            mailer.sendInvite(correo, invite.getProfessorName(), url);
        }
        return new InviteLink(url, invite.getExpiresAt(), conCorreo);
    }

    /** Lo que ve quien abre el enlace. Nunca falla: un token que no existe se muestra como vencido. */
    @Transactional(readOnly = true)
    public InvitationView view(String rawToken) {
        ProfessorInvite invite = rawToken == null ? null
                : invites.findByTokenHash(sha256Hex(rawToken)).orElse(null);
        if (invite == null) {
            return InvitationView.soloEstado(ProfessorInvite.State.EXPIRED.name());
        }
        ProfessorInvite.State state = invite.state(clock.instant());
        if (state != ProfessorInvite.State.VALID) {
            return InvitationView.soloEstado(state.name());
        }
        InvitationView.FounderOffer founder = invite.isFounder()
                ? new InvitationView.FounderOffer(settings.getInt(FounderService.RATE_KEY),
                        settings.getInt(FounderService.MONTHS_KEY), settings.getInt(BASE_RATE_KEY))
                : null;
        return new InvitationView(state.name(), invite.getEmail(), invite.getProfessorName(),
                invite.getExpiresAt().atZone(BusinessZone.BOGOTA),
                founder);
    }

    /**
     * El invitado creó su cuenta: la invitación queda usada y ligada a ella. La llama el registro, en
     * su misma transacción, así que una invitación inválida deshace también el alta.
     */
    @Transactional
    public void consume(String rawToken, User newUser) {
        ProfessorInvite invite = invites.findByTokenHash(sha256Hex(rawToken))
                .filter(i -> i.state(clock.instant()) == ProfessorInvite.State.VALID)
                .orElseThrow(() -> new UnprocessableException(
                        "La invitación ya venció o ya se usó. Escríbele a quien te invitó para que te envíe un enlace nuevo."));
        if (!invite.isFor(newUser.getEmail())) {
            throw new UnprocessableException("Esta invitación es para otro correo.");
        }
        invite.consume(newUser.getId(), clock.instant());
        invites.save(invite);
        // El enlace llegó a ese correo: pedirle que lo confirme otra vez es un paso de más. El que
        // llegó por WhatsApp no probó que el correo que escribió sea suyo: lo confirma como cualquiera.
        if (invite.hasEmail()) {
            newUser.markEmailVerified(clock.instant());
        }
    }

    /**
     * Quien fue invitado pero creó su cuenta por otro camino —«Quiero enseñar», Google, la entrada
     * normal— sigue siendo el invitado. Si su correo tiene una invitación vigente, queda usada y
     * ligada a la cuenta, que nace para enseñar; así el beneficio de fundador llega al aprobarlo y el
     * enlace ya no ofrece crear una cuenta que existe. Sin esto el beneficio se perdía en silencio.
     *
     * @return si había una invitación vigente para ese correo
     */
    @Transactional
    public boolean consumeByEmail(User newUser) {
        Instant ahora = clock.instant();
        return invites.findByEmailAndUsedAtIsNull(newUser.getEmail()).stream()
                .filter(i -> i.state(ahora) == ProfessorInvite.State.VALID)
                .findFirst()
                .map(invite -> {
                    invite.consume(newUser.getId(), ahora);
                    invites.save(invite);
                    return true;
                })
                .orElse(false);
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
