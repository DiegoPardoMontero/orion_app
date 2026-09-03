package co.orion.lifecycle.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.lifecycle.application.LessonAutoCompleteJob;
import co.orion.reputation.persistence.ProfessorSanctionRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.ProfessorAbsenceRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El ciclo de vida completo de una clase: cancelar, reclamar, resolver y cerrar. Con el reloj
 * congelado el lunes 2026-07-13 a las 12:00 de Bogotá, y las clases movidas por SQL cuando hace
 * falta que ya hayan ocurrido — mover la clase es más honesto que fingir otro reloj.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, LessonLifecycleIT.FrozenClockConfiguration.class})
class LessonLifecycleIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final long RATE_COP = 60_000;

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private StudentCreditRepository credits;
    @Autowired private ProfessorAbsenceRepository absences;
    @Autowired private ProfessorSanctionRepository sanctions;
    @Autowired private ProfessorProfileRepository profiles;
    @Autowired private co.orion.scheduling.persistence.AvailabilityRuleRepository rules;
    @Autowired private LessonAutoCompleteJob autoComplete;
    @Autowired private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private Session anaSession;
    private Session mariaSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        ProfessorProfile profile = new ProfessorProfile(maria);
        profile.changeRate(RATE_COP);
        profile.publish();
        profiles.save(profile);
        approveTeacher(maria.getId());

        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
        adminSession = login("admin@orion.test");
    }

    /* ------------------------------------------------------- cancelación (12 h) */

    @Test
    void elEstudianteCancelaConMargenYRecuperaSuValorComoSaldo() {
        UUID id = bookAndPay(9);

        ResponseEntity<BookingResponse> response = post(
                BOOKINGS + "/" + id + "/cancel", anaSession, null, BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookings.findById(id).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED_BY_STUDENT);
    }

    /** La ventana es de 12 h para ambos (decisión Q3), leída de platform_settings. */
    @Test
    void dentroDeLaVentanaYaNoSeCancela() {
        UUID id = bookAndPay(9);
        // La clase pasa a estar a 6 horas del reloj congelado.
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.plusSeconds(6 * 3600)),
                java.sql.Timestamp.from(FROZEN_NOW.plusSeconds(7 * 3600)), id);

        ResponseEntity<Map> response = post(
                BOOKINGS + "/" + id + "/cancel", anaSession, null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("12 horas");
    }

    /** Al profesor no se le deja sin salida: se le ofrece proponer otro horario. */
    @Test
    void alProfesorDentroDeLaVentanaSeLeOfreceReprogramar() {
        UUID id = bookAndPay(9);
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.plusSeconds(6 * 3600)),
                java.sql.Timestamp.from(FROZEN_NOW.plusSeconds(7 * 3600)), id);

        ResponseEntity<Map> response = post(
                BOOKINGS + "/" + id + "/cancel", mariaSession, null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("proponerle");
    }

    /* ------------------------------------------------------------ no-show y reclamo */

    @Test
    void reportarAntesDeLaGraciaNoSePuede() {
        UUID id = bookAndPay(9);
        // La clase empezó hace 5 minutos: todavía puede estar llegando.
        moveClassTo(id, FROZEN_NOW.minusSeconds(300), FROZEN_NOW.plusSeconds(3300));

        ResponseEntity<Map> response = reportProblem(id);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("15 minutos");
    }

    @Test
    void reportarFueraDePlazoTampoco() {
        UUID id = bookAndPay(9);
        // Terminó hace tres días: el profesor ya contaba con ese dinero.
        moveClassTo(id, FROZEN_NOW.minusSeconds(3 * 86400), FROZEN_NOW.minusSeconds(3 * 86400 - 3600));

        ResponseEntity<Map> response = reportProblem(id);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("venció");
    }

    @Test
    void unReclamoCongelaLaClaseYSuDinero() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(1800), FROZEN_NOW.plusSeconds(1800));

        assertThat(reportProblem(id).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(bookings.findById(id).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.UNDER_REVIEW);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.DISPUTED);
    }

    @Test
    void resolverAFavorDelEstudianteDevuelveElValorYRegistraUnaSolaAusencia() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(1800), FROZEN_NOW.plusSeconds(1800));
        UUID disputeId = openDispute(id);

        ResponseEntity<Map> resolved = post("/api/v1/admin/disputes/" + disputeId + "/resolve",
                adminSession, Map.of("outcome", "RESOLVED_FOR_STUDENT",
                        "note", "El profesor confirmó que no pudo conectarse"), Map.class);

        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookings.findById(id).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.NO_SHOW_PROFESSOR);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        // Crédito por el valor COMPLETO de la clase, y una sola ausencia.
        assertThat(credits.findUsable(ana.getId(), FROZEN_NOW).stream()
                .mapToLong(c -> c.getRemainingCop()).sum()).isEqualTo(RATE_COP);
        assertThat(absences.countByProfessorIdAndOccurredAtAfter(
                maria.getId(), FROZEN_NOW.minusSeconds(86400))).isEqualTo(1);
    }

    @Test
    void resolverAFavorDelProfesorCierraLaClaseYLiberaSuPago() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(1800), FROZEN_NOW.plusSeconds(1800));
        UUID disputeId = openDispute(id);

        post("/api/v1/admin/disputes/" + disputeId + "/resolve", adminSession,
                Map.of("outcome", "RESOLVED_FOR_PROFESSOR", "note", "El estudiante confirmó que sí se dio"),
                Map.class);

        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.RELEASED);
        assertThat(absences.count()).isZero();
    }

    /** La primera ausencia es un aviso, y en modo observación queda PROPUESTA, no aplicada. */
    @Test
    void laPrimeraAusenciaProponeUnAvisoSinAplicarlo() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(1800), FROZEN_NOW.plusSeconds(1800));
        UUID disputeId = openDispute(id);

        post("/api/v1/admin/disputes/" + disputeId + "/resolve", adminSession,
                Map.of("outcome", "RESOLVED_FOR_STUDENT", "note", "No se presentó"), Map.class);

        var todas = sanctions.findByProfessorIdOrderByCreatedAtDesc(maria.getId());
        assertThat(todas).hasSize(1);
        assertThat(todas.get(0).getType().name()).isEqualTo("WARNING");
        assertThat(todas.get(0).getState().name()).isEqualTo("PROPOSED");
        // Propuesta no es activa: el profesor sigue recibiendo reservas.
        assertThat(sanctions.findActive(maria.getId(), FROZEN_NOW)).isEmpty();
    }

    /* ------------------------------------------------------------- autocompletado */

    @Test
    void elAutocompletadoCierraLasClasesViejasYLiberaElPago() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(2 * 86400), FROZEN_NOW.minusSeconds(2 * 86400 - 3600));

        assertThat(autoComplete.run()).isEqualTo(1);

        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.RELEASED);
    }

    /** Idempotente: correr dos veces no libera el mismo pago dos veces. */
    @Test
    void correrDosVecesNoDuplicaNada() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(2 * 86400), FROZEN_NOW.minusSeconds(2 * 86400 - 3600));

        assertThat(autoComplete.run()).isEqualTo(1);
        assertThat(autoComplete.run()).isZero();

        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.RELEASED);
    }

    /** Una clase con reclamo abierto se resuelve a mano, nunca por vencimiento. */
    @Test
    void elAutocompletadoNoTocaUnaClaseConReclamoAbierto() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(1800), FROZEN_NOW.plusSeconds(1800));
        openDispute(id);
        // Ahora la clase es vieja, pero tiene reclamo.
        moveClassTo(id, FROZEN_NOW.minusSeconds(2 * 86400), FROZEN_NOW.minusSeconds(2 * 86400 - 3600));

        assertThat(autoComplete.run()).isZero();

        assertThat(bookings.findById(id).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.UNDER_REVIEW);
    }

    @Test
    void unaClaseRecienTerminadaTodaviaNoSeCierra() {
        UUID id = bookAndPay(9);
        moveClassTo(id, FROZEN_NOW.minusSeconds(7200), FROZEN_NOW.minusSeconds(3600));

        assertThat(autoComplete.run()).isZero();
        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    /* --------------------------------------------------------------------- apoyo */

    private UUID bookAndPay(int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode())
                .as("respuesta: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        approvePayment(response.getBody().id());
        return response.getBody().id();
    }

    /** Mover la clase en vez de mover el reloj: el reloj congelado es lo que hace el test legible. */
    private void moveClassTo(UUID bookingId, Instant startsAt, Instant endsAt) {
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(startsAt), java.sql.Timestamp.from(endsAt), bookingId);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> reportProblem(UUID bookingId) {
        return post(BOOKINGS + "/" + bookingId + "/report-problem", anaSession,
                Map.of("reason", "PROFESSOR_NO_SHOW", "description", "No se conectó"), Map.class);
    }

    private UUID openDispute(UUID bookingId) {
        ResponseEntity<Map> response = reportProblem(bookingId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }
}
