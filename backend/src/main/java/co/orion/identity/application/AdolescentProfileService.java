package co.orion.identity.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.AdolescentProfile;
import co.orion.identity.domain.GuardianAuthorization;
import co.orion.identity.domain.ProficiencyLevel;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.AdolescentProfileRepository;
import co.orion.identity.persistence.GuardianAuthorizationRepository;
import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.time.BusinessZone;

/**
 * Los adolescentes a cargo de un adulto (Bloque 12). Crear un perfil exige la autorización del
 * representante legal, en su versión vigente, y que el adolescente haya sido escuchado; la constancia
 * queda en {@code guardian_authorizations}. Revocarla cierra el perfil y borra lo suyo, salvo lo que
 * cuelgue de clases ya dadas o pagadas.
 */
@Service
public class AdolescentProfileService {

    private static final Logger log = LoggerFactory.getLogger(AdolescentProfileService.class);

    /** Decenas de usuarios: más de cinco perfiles bajo una cuenta es un error o un abuso. */
    static final int MAXIMO_POR_CUENTA = 5;

    private final AdolescentProfileRepository profiles;
    private final GuardianAuthorizationRepository authorizations;
    private final LegalDocumentService legal;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdolescentProfileService(AdolescentProfileRepository profiles,
                                    GuardianAuthorizationRepository authorizations,
                                    LegalDocumentService legal,
                                    JdbcTemplate jdbc,
                                    Clock clock) {
        this.profiles = profiles;
        this.authorizations = authorizations;
        this.legal = legal;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Vista(AdolescentProfile profile, Optional<GuardianAuthorization> authorization, int age) {
    }

    @Transactional(readOnly = true)
    public List<Vista> mine(User guardian) {
        requireGuardian(guardian);
        int ano = anoActual();
        return profiles.findByGuardianUserIdAndClosedAtIsNullOrderByCreatedAtAsc(guardian.getId()).stream()
                .map(p -> new Vista(p, authorizations.findByAdolescentProfileIdAndRevokedAtIsNull(p.getId()), p.ageIn(ano)))
                .toList();
    }

    @Transactional
    public Vista create(User guardian, String firstName, int birthYear, ProficiencyLevel level, String motivation,
                        boolean acceptsAuthorization, boolean heardConfirmed, String ip, String userAgent) {
        requireGuardian(guardian);
        if (!acceptsAuthorization) {
            throw new BusinessRuleViolationException(
                    "Necesitamos tu autorización como representante legal para tratar los datos del adolescente.");
        }
        if (!heardConfirmed) {
            throw new BusinessRuleViolationException(
                    "Confirma que hablaste con el adolescente y que está de acuerdo con tomar clases en Orión.");
        }
        if (profiles.countByGuardianUserIdAndClosedAtIsNull(guardian.getId()) >= MAXIMO_POR_CUENTA) {
            throw new BusinessRuleViolationException(
                    "Ya tienes " + MAXIMO_POR_CUENTA + " perfiles. Escríbenos si necesitas más.");
        }
        String version = legal.vigente(LegalDocumentCode.GUARDIAN_AUTHORIZATION).version();
        AdolescentProfile profile = profiles.saveAndFlush(
                new AdolescentProfile(guardian.getId(), firstName, birthYear, anoActual(), level, motivation));
        GuardianAuthorization authorization = authorizations.save(new GuardianAuthorization(
                profile.getId(), guardian.getId(), version, true, ip, userAgent));
        log.info("Perfil de adolescente {} creado bajo la cuenta {} con la autorización {}",
                profile.getId(), guardian.getId(), version);
        return new Vista(profile, Optional.of(authorization), profile.ageIn(anoActual()));
    }

    @Transactional
    public Vista update(User guardian, UUID profileId, String firstName, ProficiencyLevel level, String motivation) {
        AdolescentProfile profile = own(guardian, profileId);
        if (firstName != null && !firstName.isBlank()) {
            profile.rename(firstName);
        }
        profile.describe(level, motivation);
        AdolescentProfile saved = profiles.save(profile);
        return new Vista(saved, authorizations.findByAdolescentProfileIdAndRevokedAtIsNull(saved.getId()),
                saved.ageIn(anoActual()));
    }

    /**
     * Revocar la autorización: el perfil se cierra y lo suyo se borra. Si ninguna clase lo referencia,
     * desaparece entero (la constancia de la autorización cae con él, en cascada: no hay tratamiento
     * que probar). Si hay clases, la fila queda cerrada y sin nivel ni objetivo; el nombre de pila se
     * conserva porque es el soporte de a quién fue la clase que el adulto pagó.
     */
    @Transactional
    public void revoke(User guardian, UUID profileId) {
        AdolescentProfile profile = own(guardian, profileId);
        var now = clock.instant();
        authorizations.findByAdolescentProfileIdAndRevokedAtIsNull(profile.getId()).ifPresent(a -> {
            a.revoke(now);
            authorizations.save(a);
        });
        Integer clases = jdbc.queryForObject(
                "select count(*) from bookings where adolescent_profile_id = ?", Integer.class, profile.getId());
        if (clases == null || clases == 0) {
            profiles.delete(profile);
            log.info("Perfil de adolescente {} borrado al revocar la autorización", profile.getId());
            return;
        }
        profile.describe(null, null);
        profile.close(now);
        profiles.save(profile);
        log.info("Perfil de adolescente {} cerrado al revocar la autorización ({} clases lo referencian)",
                profile.getId(), clases);
    }

    /** El perfil de este adulto, vivo. Es la comprobación que usan la reserva y el aula. */
    @Transactional(readOnly = true)
    public AdolescentProfile ownAndAuthorized(User guardian, UUID profileId) {
        AdolescentProfile profile = own(guardian, profileId);
        if (authorizations.findByAdolescentProfileIdAndRevokedAtIsNull(profile.getId()).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "La autorización de " + profile.getFirstName() + " fue revocada: vuelve a crear su perfil para reservarle clases.");
        }
        if (!profile.isAdolescentIn(anoActual())) {
            throw new BusinessRuleViolationException(
                    profile.getFirstName() + " ya no está en el rango de 13 a 17 años. Puede abrir su propia cuenta.");
        }
        return profile;
    }

    @Transactional(readOnly = true)
    public Optional<AdolescentProfile> find(UUID profileId) {
        return profileId == null ? Optional.empty() : profiles.findById(profileId);
    }

    private AdolescentProfile own(User guardian, UUID profileId) {
        requireGuardian(guardian);
        return profiles.findByIdAndGuardianUserId(profileId, guardian.getId())
                .filter(p -> !p.isClosed())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
    }

    /** Solo la cuenta de un estudiante: el titular que paga y reserva. */
    private static void requireGuardian(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Solo una cuenta de estudiante puede tener adolescentes a su cargo.");
        }
    }

    private int anoActual() {
        return LocalDate.now(clock.withZone(BusinessZone.BOGOTA)).getYear();
    }
}
