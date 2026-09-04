package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.domain.AvailabilityException;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityExceptionRepository;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AvailabilityExceptionIT extends ApiIntegrationSupport {

    private static final String EXCEPTIONS = "/api/v1/me/availability/exceptions";

    @Autowired
    private AvailabilityExceptionRepository exceptions;

    @Autowired
    private Clock clock;

    private User maria;
    private User juan;
    private Session mariaSession;
    private LocalDate today;

    @BeforeEach
    void seed() {
        exceptions.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        mariaSession = login("maria@orion.test");
        today = LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
    }

    @Test
    void createsAWholeDayBlock() {
        ResponseEntity<ExceptionResponse> response = post(
                EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(3), null, null, "Vacaciones"),
                ExceptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().date()).isEqualTo(today.plusDays(3));
        // Tiempos nulos es exactamente lo que significa "día completo".
        assertThat(response.getBody().startTime()).isNull();
        assertThat(response.getBody().endTime()).isNull();
        assertThat(response.getBody().reason()).isEqualTo("Vacaciones");
    }

    @Test
    void createsAPartialBlock() {
        ResponseEntity<ExceptionResponse> response = post(
                EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0), "Cita médica"),
                ExceptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.getBody().endTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void acceptsAPartialBlockThatIsNotAlignedToTheHour() {
        // A diferencia de las reglas, las excepciones parciales no exigen minutos en :00.
        ResponseEntity<ExceptionResponse> response = post(
                EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(2), LocalTime.of(10, 30), LocalTime.of(11, 30), null),
                ExceptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void rejectsABlockWithOnlyOneOfTheTwoTimes() {
        ResponseEntity<Map> response = post(
                EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(1), LocalTime.of(10, 0), null, null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("juntos");
    }

    @Test
    void rejectsAnEndTimeThatIsNotAfterTheStartTime() {
        ResponseEntity<Map> response = post(
                EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(1), LocalTime.of(11, 0), LocalTime.of(10, 0), null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("anterior a endTime");
    }

    @Test
    void listsOnlyUpcomingExceptionsOrderedByDate() {
        exceptions.save(AvailabilityException.wholeDay(maria.getId(), today.minusDays(1), "Ayer"));
        post(EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(5), null, null, "Lejos"), ExceptionResponse.class);
        post(EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(1), null, null, "Pronto"), ExceptionResponse.class);

        ResponseEntity<ExceptionResponse[]> response = get(EXCEPTIONS, mariaSession, ExceptionResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // La de ayer no aparece; las futuras salen ordenadas por fecha.
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].reason()).isEqualTo("Pronto");
        assertThat(response.getBody()[1].reason()).isEqualTo("Lejos");
    }

    @Test
    void listingOnlyReturnsTheExceptionsOfTheLoggedInProfessor() {
        exceptions.save(AvailabilityException.wholeDay(juan.getId(), today.plusDays(1), "De Juan"));
        post(EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(1), null, null, "De María"), ExceptionResponse.class);

        ResponseEntity<ExceptionResponse[]> response = get(EXCEPTIONS, mariaSession, ExceptionResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].reason()).isEqualTo("De María");
    }

    @Test
    void aStudentCannotManageExceptions() {
        Session ana = login("ana@orion.test");

        ResponseEntity<Map> response = get(EXCEPTIONS, ana, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletesOwnException() {
        UUID id = post(EXCEPTIONS, mariaSession,
                new CreateExceptionRequest(today.plusDays(1), null, null, null),
                ExceptionResponse.class).getBody().id();

        ResponseEntity<Void> response = delete(EXCEPTIONS + "/" + id, mariaSession, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exceptions.findById(id)).isEmpty();
    }

    @Test
    void deletingAnotherProfessorsExceptionReturnsNotFoundAndLeavesItAlone() {
        AvailabilityException juans = exceptions.save(
                AvailabilityException.wholeDay(juan.getId(), today.plusDays(1), "De Juan"));

        ResponseEntity<Map> response = delete(EXCEPTIONS + "/" + juans.getId(), mariaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exceptions.findById(juans.getId())).isPresent();
    }
}
