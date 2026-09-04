package co.orion.identity.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ApplicationEventType;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.TeacherApplicationEvent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorGoalRepository;
import co.orion.identity.persistence.ProfessorLanguageLevelRepository;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationEventRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;

/**
 * Datos de desarrollo. Idempotente: cada usuario se crea solo si su email no existe todavía.
 * Los profesores nacen con tarifa, idioma y objetivos para poblar el marketplace desde el arranque.
 */
// El primero de los runners: los demás lo dan por hecho. `BillingDevSeeder` va en 100 y solo
// siembra saldo si la estudiante ya existe, así que sin este orden explícito, sobre una base
// recién creada, Ana nacía sin saldo y no había forma de confirmar una clase en local.
@Component
@Profile("local")
@Order(0)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String DEV_PASSWORD = "orion123*";

    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final ProfessorLanguageRepository languages;
    private final ProfessorLanguageLevelRepository levels;
    private final ProfessorGoalRepository goals;
    private final AvailabilityRuleRepository rules;
    private final BookingRepository bookings;
    private final Clock clock;
    private final TeacherApplicationRepository applications;
    private final TeacherApplicationEventRepository applicationEvents;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DevDataSeeder(UserRepository users,
                         ProfessorProfileRepository profiles,
                         ProfessorLanguageRepository languages,
                         ProfessorLanguageLevelRepository levels,
                         ProfessorGoalRepository goals,
                         AvailabilityRuleRepository rules,
                         BookingRepository bookings,
                         Clock clock,
                         TeacherApplicationRepository applications,
                         TeacherApplicationEventRepository applicationEvents,
                         PasswordEncoder passwordEncoder,
                         @Value("${orion.admin.email}") String adminEmail,
                         @Value("${orion.admin.password}") String adminPassword) {
        this.users = users;
        this.profiles = profiles;
        this.languages = languages;
        this.levels = levels;
        this.goals = goals;
        this.rules = rules;
        this.bookings = bookings;
        this.clock = clock;
        this.applications = applications;
        this.applicationEvents = applicationEvents;
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

        seedHistorialDeAna();
    }

    /**
     * Cuatro clases pasadas de Ana, ya cerradas.
     *
     * <p>Sin historial, un entorno de desarrollo recién levantado enseña la gamificación entera
     * vacía —cielo apagado, racha en cero, ninguna pieza que ponerse— y no hay forma de mirar lo
     * que se construyó. Las cuatro van en semanas distintas y con dos profesores para que la racha,
     * el volumen y la amplitud tengan algo que contar. El backfill de {@code engagement}, que corre
     * después de esto, es quien enciende lo que corresponda.
     */
    private void seedHistorialDeAna() {
        Optional<User> estudiante = users.findByEmailIgnoreCase("ana@orion.local");
        Optional<User> maria = users.findByEmailIgnoreCase("maria@orion.local");
        Optional<User> juan = users.findByEmailIgnoreCase("juan@orion.local");
        if (estudiante.isEmpty() || maria.isEmpty() || juan.isEmpty()) {
            return;
        }
        UUID anaId = estudiante.get().getId();
        // Idempotente como el resto: si ya tiene clases cerradas, no se siembran otras.
        if (bookings.existsByStudentIdAndStatus(anaId, BookingStatus.COMPLETED)) {
            return;
        }

        Instant ahora = clock.instant();
        record ClasePasada(UUID profesor, int semanasAtras, BookingModality modalidad, String idioma) {
        }
        List<ClasePasada> historial = List.of(
                new ClasePasada(maria.get().getId(), 4, BookingModality.VIRTUAL, "EN"),
                new ClasePasada(maria.get().getId(), 3, BookingModality.VIRTUAL, "EN"),
                new ClasePasada(juan.get().getId(), 2, BookingModality.IN_PERSON, "FR"),
                new ClasePasada(maria.get().getId(), 1, BookingModality.VIRTUAL, "EN"));

        for (ClasePasada clase : historial) {
            Instant inicio = ZonedDateTime.ofInstant(ahora, BusinessZone.BOGOTA)
                    .minusWeeks(clase.semanasAtras())
                    .withHour(18).withMinute(0).withSecond(0).withNano(0)
                    .toInstant();
            Booking booking = new Booking(anaId, clase.profesor(), inicio, inicio.plus(Duration.ofHours(1)),
                    clase.modalidad(), null, clase.idioma(), anaId, inicio);
            booking.confirmPayment();
            booking.autoComplete(inicio.plus(Duration.ofHours(1)));
            bookings.save(booking);
        }
        log.info("Sembradas {} clases pasadas de Ana para poder ver la gamificación.", historial.size());
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
                    profile.describe("Profesora de inglés conversacional para profesionales",
                            "Llevo diez años enseñando inglés a profesionales colombianos que "
                                    + "necesitan hablar en reuniones sin quedarse en blanco. Mis "
                                    + "clases se apoyan en tu trabajo real: practicamos con tus "
                                    + "correos, tus presentaciones y las conversaciones que de "
                                    + "verdad te tocan cada semana.");
                    profile.enrich("CO", "Bogotá", "ES", (short) 10,
                            "Lic. en Lenguas Modernas, Universidad Nacional", true, true);
                    profile.changeRate(45000L);
                    profile.publish();
                    profiles.save(profile);
                    approveTeacher(professor.getId());
                    seedTaxonomy(professor.getId(), "EN", false,
                            List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"),
                            List.of("CONVERSATION", "BUSINESS"));
                });
    }

    private void seedJuan() {
        createIfMissing("juan@orion.local", "Juan Torres", UserRole.PROFESSOR, DEV_PASSWORD)
                .ifPresent(professor -> {
                    ProfessorProfile profile = new ProfessorProfile(professor);
                    profile.describe("Profesor de francés práctico para viajeros",
                            "Enseño el francés que se usa fuera del aula: pedir en un "
                                    + "restaurante, resolver un problema en el aeropuerto o "
                                    + "sostener una charla con alguien que acabas de conocer. "
                                    + "Empezamos hablando desde la primera clase, aunque sea con "
                                    + "frases sueltas.");
                    profile.enrich("CO", "Medellín", "ES", (short) 6,
                            "Certificación DELF C1", false, true);
                    profile.changeRate(55000L);
                    profile.publish();
                    profiles.save(profile);
                    approveTeacher(professor.getId());
                    seedTaxonomy(professor.getId(), "FR", false,
                            List.of("BEGINNER", "INTERMEDIATE"),
                            List.of("TRAVEL", "CONVERSATION"));
                });
    }

    /** Sin postulación APPROVED el gate ocultaría a los profesores sembrados del marketplace. */
    private void approveTeacher(UUID professorId) {
        if (applications.existsByUserIdAndStatus(professorId, ApplicationStatus.APPROVED)) {
            return;
        }
        TeacherApplication application = applications.saveAndFlush(new TeacherApplication(
                professorId, ApplicationStatus.APPROVED, null, java.time.Instant.now()));
        applicationEvents.save(new TeacherApplicationEvent(
                application.getId(), ApplicationEventType.APPROVED, null, "Semilla de desarrollo"));
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
