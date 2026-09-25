package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * La clase de prueba del estudiante: GRATIS si el profesor la ofrece (V65; antes tenía precio
 * propio), una por pareja y para conocerse. Distinta del ensayo del admin, que sigue su camino
 * (ClaseDePruebaIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, ClaseDePruebaDelEstudianteIT.FrozenClockConfiguration.class})
class ClaseDePruebaDelEstudianteIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 7, 15);

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

    private User maria;
    private Session anaSession;
    private Session mariaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ProfessorProfile perfil = new ProfessorProfile(maria);
        perfil.changeRate(60_000L);
        perfil.publish();
        profiles.save(perfil);
        approveTeacher(maria.getId());
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
    }

    /** María la enciende desde su perfil, como en la pantalla: un interruptor, sin precio. */
    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> ofrecer(boolean si) {
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("acceptsTrial", si);
        perfil.put("isPublished", true);
        perfil.put("languages", List.of());
        perfil.put("goals", List.of());
        return put("/api/v1/me/profile", mariaSession, perfil, Map.class);
    }

    private CreateBookingRequest reserva(int hora, boolean prueba) {
        OffsetDateTime at = ZonedDateTime.of(MIERCOLES, LocalTime.of(hora, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        return new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null, null, prueba);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un profesor nuevo no la ofrece: regalar una hora lo decide él")
    void apagadaPorDefecto() {
        Map detalle = get("/api/v1/professors/" + maria.getId(), anaSession, Map.class).getBody();
        assertThat(detalle).containsEntry("acceptsTrial", false).doesNotContainKey("trialPriceCop");

        assertThat(get("/api/v1/professors/" + maria.getId() + "/trial", anaSession, Map.class).getBody())
                .containsEntry("offered", false).containsEntry("available", false);
        assertThat(post("/api/v1/bookings", anaSession, reserva(9, true), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Encendida, es gratis: se confirma en el acto, sin pasarela ni comisión, y queda marcada como prueba")
    void esGratis() {
        assertThat(ofrecer(true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/professors/" + maria.getId(), anaSession, Map.class).getBody())
                .containsEntry("acceptsTrial", true);
        assertThat(get("/api/v1/professors/" + maria.getId() + "/trial", anaSession, Map.class).getBody())
                .containsEntry("offered", true).containsEntry("available", true).doesNotContainKey("priceCop");

        ResponseEntity<BookingResponse> creada = post("/api/v1/bookings", anaSession, reserva(9, true), BookingResponse.class);

        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creada.getBody().status()).isEqualTo(BookingStatus.CONFIRMED.name());
        UUID id = creada.getBody().id();
        assertThat(bookings.findById(id).orElseThrow().isTrial()).isTrue();
        var pago = payments.findByBookingId(id).orElseThrow();
        assertThat(pago.getAmountCop()).isZero();
        assertThat(pago.getChargedCop()).isZero();
        assertThat(pago.getCommissionCop()).isZero();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Apagarla la quita del perfil y de la reserva")
    void seApaga() {
        ofrecer(true);
        assertThat(ofrecer(false).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/professors/" + maria.getId() + "/trial", anaSession, Map.class).getBody())
                .containsEntry("offered", false);
        assertThat(post("/api/v1/bookings", anaSession, reserva(9, true), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una por pareja: con la prueba en curso, no hay segunda; tampoco para quien ya tiene clases")
    void unaPorPareja() {
        ofrecer(true);
        ResponseEntity<BookingResponse> primera = post("/api/v1/bookings", anaSession, reserva(9, true), BookingResponse.class);
        assertThat(primera.getBody().status()).isEqualTo(BookingStatus.CONFIRMED.name());

        ResponseEntity<Map> segunda = post("/api/v1/bookings", anaSession, reserva(10, true), Map.class);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(get("/api/v1/professors/" + maria.getId() + "/trial", anaSession, Map.class).getBody())
                .containsEntry("available", false);

        // Una clase normal sí se puede reservar después de la prueba.
        assertThat(post("/api/v1/bookings", anaSession, reserva(11, false), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Si la prueba se cancela, no se gasta: se puede volver a reservar")
    void laCanceladaNoCuenta() {
        ofrecer(true);
        UUID id = post("/api/v1/bookings", anaSession, reserva(9, true), BookingResponse.class).getBody().id();
        assertThat(post("/api/v1/bookings/" + id + "/cancel", anaSession, Map.of(), Map.class).getStatusCode().is2xxSuccessful())
                .isTrue();

        assertThat(post("/api/v1/bookings", anaSession, reserva(10, true), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("El índice de la base es el árbitro final: dos pruebas activas de la misma pareja no entran")
    void elIndiceEsElArbitro() {
        ofrecer(true);
        UUID id = post("/api/v1/bookings", anaSession, reserva(9, true), BookingResponse.class).getBody().id();
        var otra = bookings.findById(id).orElseThrow();
        Booking copia = TestBookings.confirmed(otra.getStudentId(), otra.getProfessorId(),
                otra.getStartsAt().plusSeconds(7200), BookingModality.VIRTUAL, null, otra.getStudentId());
        copia.markAsTrial();
        assertThrows(DataIntegrityViolationException.class, () -> bookings.saveAndFlush(copia));
    }
}
