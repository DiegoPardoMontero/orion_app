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
 * Reenviar invalida la anterior sin usar del mismo correo.
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

    /**
     * El admin invita a un profe por correo. Si ya hay una cuenta con ese correo no se invita: si es
     * un profe, el beneficio de fundador se le otorga desde Usuarios.
     *
     * @param inviterTitle el cargo del admin, si lo escribió: queda en su cuenta para las siguientes
     */
    @Transactional
    public void invite(UUID adminId, String email, String professorName, boolean founder, String inviterTitle) {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(correo)) {
            throw new ConflictException(
                    "Ya existe una cuenta con ese correo. Si es un profe, dale el beneficio de fundador desde Usuarios.");
        }
        User admin = users.findById(adminId).orElse(null);
        if (admin != null && inviterTitle != null) {
            admin.changeJobTitle(inviterTitle);
            users.save(admin);
        }

        invites.deleteUnusedByEmail(correo);
        String rawToken = randomToken();
        ProfessorInvite invite = invites.saveAndFlush(new ProfessorInvite(correo, professorName, adminId, founder,
                sha256Hex(rawToken), clock.instant().plus(TTL)));

        mailer.sendInvite(correo, invite.getProfessorName(), admin == null ? null : admin.getFullName(),
                baseUrl + "/invitacion/" + rawToken);
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
        User admin = invite.getInvitedBy() == null ? null : users.findById(invite.getInvitedBy()).orElse(null);
        InvitationView.FounderOffer founder = invite.isFounder()
                ? new InvitationView.FounderOffer(settings.getInt(FounderService.RATE_KEY),
                        settings.getInt(FounderService.MONTHS_KEY), settings.getInt(BASE_RATE_KEY))
                : null;
        return new InvitationView(state.name(), invite.getEmail(), invite.getProfessorName(),
                admin == null ? null : admin.getFullName(),
                admin == null ? null : admin.getJobTitle(),
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
                        "La invitación ya venció o ya se usó. Escríbele a quien te invitó y te enviamos un enlace nuevo."));
        if (!invite.isFor(newUser.getEmail())) {
            throw new UnprocessableException("Esta invitación es para otro correo.");
        }
        invite.consume(newUser.getId(), clock.instant());
        invites.save(invite);
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
