package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.support.ApiIntegrationSupport;

import java.time.DayOfWeek;

/** Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, CreateBookingIT.FrozenClockConfiguration.class})
class CreateBookingIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    /** Tarifa de María. Sin tarifa no hay precio que cobrar y la reserva no se puede crear. */
    private static final long RATE_COP = 60_000;

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private AvailabilityRuleRepository rules;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private PaymentRepository payments;

    private User ana;
    private User carlos;
    private User maria;
    private User juan;
    private Session anaSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        payments.deleteAll();
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        ProfessorProfile published = new ProfessorProfile(maria);
        published.changeRate(RATE_COP);
        published.publish();
        profiles.save(published);
        ProfessorProfile unpublished = new ProfessorProfile(juan); // sin publicar
        unpublished.changeRate(RATE_COP);
        profiles.save(unpublished);
        // Ambos aprobados: el gate no debe ocultarlos. Juan sigue sin publicar (404 al reservarlo).
        approveTeacher(maria.getId());
        approveTeacher(juan.getId());

        // María: miércoles 08:00–11:00 → cupos a las 8, 9 y 10.
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));
        rules.save(new AvailabilityRule(juan.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        adminSession = login("admin@orion.test");
    }

    private OffsetDateTime wednesdayAt(int hour) {
        return ZonedDateTime.of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
    }

    private CreateBookingRequest request(UUID professorId, int hour, UUID studentId) {
        return new CreateBookingRequest(professorId, wednesdayAt(hour), "VIRTUAL", null, null, studentId);
    }

    @Test
    void aStudentBooksAnAvailableSlotForThemselves() {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, anaSession, request(maria.getId(), 9, null), BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Desde el Bloque 4 una reserva nace sin pagar: es una clase apartada, no una clase.
        assertThat(response.getBody().status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getBody().studentId()).isEqualTo(ana.getId());
        assertThat(response.getBody().endsAt()).isEqualTo(response.getBody().startsAt().plusHours(1));

        var saved = bookings.findById(response.getBody().id()).orElseThrow();
        // created_by == student_id: es una reserva de autoservicio, la métrica estrella del MVP.
        assertThat(saved.getCreatedBy()).isEqualTo(ana.getId());
        assertThat(saved.isSelfService()).isTrue();
        assertThat(saved.getExpiresAt()).isNotNull();
    }

    @Test
    void theBookingComesWithWhatToPayAndWhereToPayIt() {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, anaSession, request(maria.getId(), 9, null), BookingResponse.class);

        var payment = response.getBody().payment();
        assertThat(payment.amountCop()).isEqualTo(RATE_COP);
        assertThat(payment.creditAppliedCop()).isZero();
        assertThat(payment.chargedCop()).isEqualTo(RATE_COP);
        assertThat(payment.checkoutUrl())
                .startsWith("https://checkout.wompi.co/p/")
                .contains("amount-in-cents=" + RATE_COP * 100)
                .contains("signature%3Aintegrity".replace("%3A", ":"));
    }

    /**
     * La sala virtual se crea al CONFIRMAR, no al apartar el cupo: el enlace viaja en el correo de
     * confirmación, y una reserva sin pagar no genera correo ni sala.
     */
    @Test
    void aVirtualBookingGetsAJitsiMeetingLinkOnceItIsPaid() {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, anaSession, request(maria.getId(), 9, null), BookingResponse.class);

        var pending = bookings.findById(response.getBody().id()).orElseThrow();
        assertThat(pending.getMeetingLink()).isNull();

        approvePayment(response.getBody().id());

        var paid = bookings.findById(response.getBody().id()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paid.getMeetingLink()).startsWith("https://meet.jit.si/OrionIdiomas-");
    }

    @Test
    void anInPersonBookingHasNoMeetingLink() {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, anaSession,
                new CreateBookingRequest(maria.getId(), wednesdayAt(9), "IN_PERSON", "Café del centro", null, null),
                BookingResponse.class);

        var saved = bookings.findById(response.getBody().id()).orElseThrow();
        assertThat(saved.getMeetingLink()).isNull();
    }

    @Test
    void bookingRemovesTheSlotFromTheAvailableOnes() {
        post(BOOKINGS, anaSession, request(maria.getId(), 9, null), BookingResponse.class);

        ResponseEntity<SlotsResponse> slots = get(
                "/api/v1/professors/" + maria.getId() + "/slots?from=2026-07-15&to=2026-07-15",
                anaSession, SlotsResponse.class);

        assertThat(slots.getBody().slots()).hasSize(2);
    }

    @Test
    void bookingAnAlreadyBookedSlotIsUnprocessable() {
        post(BOOKINGS, anaSession, request(maria.getId(), 9, null), BookingResponse.class);
        Session carlosSession = login("carlos@orion.test");

        ResponseEntity<Map> response = post(
                BOOKINGS, carlosSession, request(maria.getId(), 9, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("no está disponible");
    }

    @Test
    void bookingATimeThatIsNotASlotIsUnprocessable() {
        // Las 12:00 del miércoles no existe: la regla termina a las 11:00.
        ResponseEntity<Map> response = post(
                BOOKINGS, anaSession, request(maria.getId(), 12, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void bookingWithAnUnpublishedProfessorIsNotFound() {
        ResponseEntity<Map> response = post(
                BOOKINGS, anaSession, request(juan.getId(), 9, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aStudentCannotHaveTwoOverlappingBookingsEvenWithDifferentProfessors() {
        ProfessorProfile juanProfile = profiles.findById(juan.getId()).orElseThrow();
        juanProfile.publish();
        profiles.save(juanProfile);

        post(BOOKINGS, anaSession, request(maria.getId(), 9, null), BookingResponse.class);

        // El cupo de Juan a las 9 está libre, pero Ana no puede estar en dos clases a la vez.
        ResponseEntity<Map> response = post(
                BOOKINGS, anaSession, request(juan.getId(), 9, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("ya tiene una clase");
    }

    @Test
    void anInvalidModalityIsRejected() {
        ResponseEntity<Map> response = post(
                BOOKINGS, anaSession,
                new CreateBookingRequest(maria.getId(), wednesdayAt(9), "TELEPATIA", null, null, null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("VIRTUAL");
    }

    @Test
    void aStudentCannotBookOnBehalfOfAnotherStudent() {
        ResponseEntity<Map> response = post(
                BOOKINGS, anaSession, request(maria.getId(), 9, carlos.getId()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aProfessorCannotBook() {
        Session mariaSession = login("maria@orion.test");

        ResponseEntity<Map> response = post(
                BOOKINGS, mariaSession, request(maria.getId(), 9, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAdminBooksOnBehalfOfAStudent() {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, adminSession, request(maria.getId(), 9, ana.getId()), BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().studentId()).isEqualTo(ana.getId());

        var saved = bookings.findById(response.getBody().id()).orElseThrow();
        // created_by es el admin, no la estudiante: esta reserva NO cuenta como autoservicio.
        assertThat(saved.getCreatedBy()).isNotEqualTo(ana.getId());
        assertThat(saved.isSelfService()).isFalse();
    }

    @Test
    void anAdminMustSayWhoTheStudentIs() {
        ResponseEntity<Map> response = post(
                BOOKINGS, adminSession, request(maria.getId(), 9, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAdminCannotBookOnBehalfOfAProfessor() {
        ResponseEntity<Map> response = post(
                BOOKINGS, adminSession, request(maria.getId(), 9, juan.getId()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * La carrera: dos estudiantes distintos van al mismo cupo a la vez. El chequeo amable puede
     * dejar pasar a ambos, pero el índice único parcial solo deja entrar a uno. Exactamente una
     * reserva sobrevive y el perdedor recibe un error (409 si perdió en el INSERT, 422 si para
     * cuando llegó al chequeo el otro ya había confirmado).
     */
    @Test
    void twoStudentsRacingForTheSameSlotResultInExactlyOneBooking() throws Exception {
        Session carlosSession = login("carlos@orion.test");
        CyclicBarrier startLine = new CyclicBarrier(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> anaAttempt = pool.submit(racer(startLine, anaSession));
            Future<ResponseEntity<Map>> carlosAttempt = pool.submit(racer(startLine, carlosSession));

            List<ResponseEntity<Map>> results = List.of(anaAttempt.get(), carlosAttempt.get());

            long created = results.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                    .count();
            long rejected = results.stream()
                    .filter(response -> response.getStatusCode().value() == 409
                            || response.getStatusCode().value() == 422)
                    .count();

            assertThat(created)
                    .as("resultados de la carrera: %s", results.stream()
                            .map(r -> r.getStatusCode() + " " + r.getBody()).toList())
                    .isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(bookings.findAll().stream()
                .filter(booking -> booking.getStatus().occupiesSlot())
                .toList()).hasSize(1);
    }

    private Callable<ResponseEntity<Map>> racer(CyclicBarrier startLine, Session session) {
        return () -> {
            startLine.await();
            return post(BOOKINGS, session, request(maria.getId(), 9, null), Map.class);
        };
    }
}
