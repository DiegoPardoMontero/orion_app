package co.orion.identity.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorGoalRepository;
import co.orion.identity.persistence.ProfessorLanguageLevelRepository;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;

/**
 * Datos de desarrollo. Idempotente: cada usuario se crea solo si su email no existe todavía.
 * Los profesores nacen con tarifa, idioma y objetivos para poblar el marketplace desde el arranque.
 */
@Component
@Profile("local")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String DEV_PASSWORD = "orion123*";

    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final ProfessorLanguageRepository languages;
    private final ProfessorLanguageLevelRepository levels;
    private final ProfessorGoalRepository goals;
    private final AvailabilityRuleRepository rules;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DevDataSeeder(UserRepository users,
                         ProfessorProfileRepository profiles,
                         ProfessorLanguageRepository languages,
                         ProfessorLanguageLevelRepository levels,
                         ProfessorGoalRepository goals,
                         AvailabilityRuleRepository rules,
                         PasswordEncoder passwordEncoder,
                         @Value("${orion.admin.email}") String adminEmail,
                         @Value("${orion.admin.password}") String adminPassword) {
        this.users = users;
        this.profiles = profiles;
        this.languages = languages;
        this.levels = levels;
        this.goals = goals;
        this.rules = rules;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedMaria();
        seedJuan();
        seedStudent("ana@orion.local", "Ana Ramírez");
        seedStudent("carlos@orion.local", "Carlos Peña");

        seedAvailability("maria@orion.local",
                new RuleSpec(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                new RuleSpec(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(11, 0)));
        seedAvailability("juan@orion.local",
                new RuleSpec(DayOfWeek.TUESDAY, LocalTime.of(15, 0), LocalTime.of(18, 0)));
    }

    private record RuleSpec(DayOfWeek weekday, LocalTime startTime, LocalTime endTime) {
    }

    private void seedAvailability(String professorEmail, RuleSpec... specs) {
        users.findByEmailIgnoreCase(professorEmail).ifPresent(professor -> {
            if (!rules.findByProfessorIdAndActiveTrue(professor.getId()).isEmpty()) {
                return;
            }
            for (RuleSpec spec : specs) {
                rules.save(new AvailabilityRule(
                        professor.getId(), spec.weekday(), spec.startTime(), spec.endTime()));
                log.info("Semilla: regla {} {}–{} para {}",
                        spec.weekday(), spec.startTime(), spec.endTime(), professorEmail);
            }
        });
    }

    private void seedAdmin() {
        createIfMissing(adminEmail, "Orion Admin", UserRole.ADMIN, adminPassword);
    }

    private void seedMaria() {
        createIfMissing("maria@orion.local", "María Gómez", UserRole.PROFESSOR, DEV_PASSWORD)
                .ifPresent(professor -> {
                    ProfessorProfile profile = new ProfessorProfile(professor);
                    profile.describe("Profesora de inglés conversacional",
                            "Diez años enseñando inglés a profesionales colombianos. "
                                    + "Clases enfocadas en fluidez y confianza al hablar.");
                    profile.enrich("CO", "Bogotá", "ES", (short) 10,
                            "Lic. en Lenguas Modernas, Universidad Nacional", true, true);
                    profile.changeRate(45000L);
                    profile.publish();
                    profiles.save(profile);
                    seedTaxonomy(professor.getId(), "EN", false,
                            List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"),
                            List.of("CONVERSATION", "BUSINESS"));
                });
    }

    private void seedJuan() {
        createIfMissing("juan@orion.local", "Juan Torres", UserRole.PROFESSOR, DEV_PASSWORD)
                .ifPresent(professor -> {
                    ProfessorProfile profile = new ProfessorProfile(professor);
                    profile.describe("Profesor de francés para viajeros",
                            "Francés práctico para viajar y conversar sin miedo.");
                    profile.enrich("CO", "Medellín", "ES", (short) 6,
                            "Certificación DELF C1", false, true);
                    profile.changeRate(55000L);
                    profile.publish();
                    profiles.save(profile);
                    seedTaxonomy(professor.getId(), "FR", false,
                            List.of("BEGINNER", "INTERMEDIATE"),
                            List.of("TRAVEL", "CONVERSATION"));
                });
    }

    private void seedTaxonomy(UUID professorId, String languageCode, boolean isNative,
                              List<String> levelCodes, List<String> goalCodes) {
        languages.save(new ProfessorLanguage(professorId, languageCode, isNative));
        for (String level : levelCodes) {
            levels.save(new ProfessorLanguageLevel(professorId, languageCode, level));
        }
        for (String goal : goalCodes) {
            goals.save(new ProfessorGoal(professorId, goal));
        }
    }

    private void seedStudent(String email, String fullName) {
        createIfMissing(email, fullName, UserRole.STUDENT, DEV_PASSWORD);
    }

    private Optional<User> createIfMissing(String email, String fullName, UserRole role, String rawPassword) {
        if (users.existsByEmailIgnoreCase(email)) {
            return Optional.empty();
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), fullName, role);
        User saved = users.save(user);
        log.info("Semilla: usuario {} creado con rol {}", saved.getEmail(), role);
        return Optional.of(saved);
    }
}
