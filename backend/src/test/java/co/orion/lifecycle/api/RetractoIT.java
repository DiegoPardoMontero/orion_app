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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.RefundRequestRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * El derecho de retracto del art. 47 de la Ley 1480 de 2011.
 *
 * <p>Reloj congelado el lunes 13 de julio de 2026 al mediodía de Bogotá. Cuando hace falta que la
 * reserva sea vieja, se mueve su fecha de creación por SQL: mover el dato es más honesto que fingir
 * otro reloj, y es lo que ya hace {@code LessonLifecycleIT} con las clases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, RetractoIT.FrozenClockConfiguration.class})
class RetractoIT extends ApiIntegrationSupport {

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
    @Autowired private RefundRequestRepository refunds;
    @Autowired private StudentCreditRepository credits;
    @Autowired private ProfessorProfileRepository profiles;
    @Autowired private AvailabilityRuleRepository rules;
    @Autowired private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private Session anaSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        refunds.deleteAll();
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        ProfessorProfile perfil = new ProfessorProfile(maria);
        perfil.changeRate(RATE_COP);
        perfil.publish();
        profiles.save(perfil);
        approveTeacher(maria.getId());

        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        adminSession = login("admin@orion.test");
    }

    private UUID bookAndPay(int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode())
                .as("respuesta: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        approvePayment(response.getBody().id());
        return response.getBody().id();
    }

    /** Envejece la reserva: es lo que hace que el plazo de 5 días hábiles se pueda probar. */
    private void reservadaHace(UUID bookingId, Instant cuando) {
        jdbc.update("update bookings set created_at = ? where id = ?",
                java.sql.Timestamp.from(cuando), bookingId);
    }

    private String retraccion(UUID bookingId) {
        return "/api/v1/me/bookings/" + bookingId + "/retraction";
    }

    /* ------------------------------------------------------------------ el derecho */

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una clase reservada hoy y aún por dar admite retracto")
    void elRetractoAplica() {
        UUID id = bookAndPay(9);

        ResponseEntity<Map> elegibilidad = get(retraccion(id), anaSession, Map.class);

        assertThat(elegibilidad.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elegibilidad.getBody()).containsEntry("eligible", true);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Ejercerlo cancela la clase, congela el pago y crea la devolución")
    void ejercerloCongelaElDinero() {
        UUID id = bookAndPay(9);

        ResponseEntity<Map> respuesta = post(retraccion(id), anaSession, null, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookings.findById(id).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED_BY_STUDENT);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(refunds.findByBookingId(id)).isPresent();
        assertThat(refunds.findByBookingId(id).orElseThrow().getAmountCop()).isEqualTo(RATE_COP);
    }

    /**
     * Lo que separa el retracto de una cancelación: el dinero se le debe al estudiante en su medio
     * de pago, NO se le abona como saldo. Si esto abonara saldo, estaríamos incumpliendo el art. 47
     * mientras la pantalla dice que lo cumplimos.
     */
    @Test
    @DisplayName("El retracto NO abona saldo: el dinero vuelve al medio de pago")
    void elRetractoNoAbonaSaldo() {
        UUID id = bookAndPay(9);

        post(retraccion(id), anaSession, null, Map.class);

        assertThat(credits.findAll()).isEmpty();
    }

    /** El pago congelado no puede acabar en una liquidación por un camino descuidado. */
    @Test
    @DisplayName("Un pago en retracto nunca se puede liberar al profesor")
    void unPagoEnRetractoNoSeLibera() {
        UUID id = bookAndPay(9);
        post(retraccion(id), anaSession, null, Map.class);

        var pago = payments.findByBookingId(id).orElseThrow();

        assertThat(pago.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(pago.getStatus().isFrozen()).isTrue();
        assertThat(org.assertj.core.api.Assertions
                .catchThrowable(() -> pago.release(FROZEN_NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    /* ------------------------------------------------------------------ los límites */

    /**
     * La excepción del art. 47: no hay retracto sobre un servicio cuya prestación ya comenzó con
     * acuerdo del consumidor.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una clase que ya empezó no admite retracto")
    void laClaseQueYaEmpezoNoAdmiteRetracto() {
        UUID id = bookAndPay(9);
        // La movemos al pasado: el miércoles a las 9 pasa a ser el domingo anterior.
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(7200)),
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(3600)), id);

        ResponseEntity<Map> respuesta = post(retraccion(id), anaSession, null, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(respuesta.getBody().get("error"))).contains("ya empezó");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Pasados los 5 días hábiles desde la reserva, ya no se puede")
    void pasadoElPlazoYaNoSePuede() {
        UUID id = bookAndPay(9);
        // Reservada hace tres semanas: muy por fuera de los 5 días hábiles.
        reservadaHace(id, FROZEN_NOW.minus(java.time.Duration.ofDays(21)));

        ResponseEntity<Map> respuesta = post(retraccion(id), anaSession, null, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(respuesta.getBody().get("error"))).contains("5 días hábiles");
    }

    /**
     * El fin de semana no cuenta. Una reserva del jueves anterior sigue dentro del plazo el lunes,
     * porque sábado y domingo no son hábiles — si contáramos días corridos, le habríamos quitado
     * dos días de derecho.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El fin de semana no consume plazo")
    void elFinDeSemanaNoConsumePlazo() {
        UUID id = bookAndPay(9);
        // Jueves 9 de julio: a días corridos serían 4; a días hábiles, 2.
        reservadaHace(id, Instant.parse("2026-07-09T15:00:00Z"));

        ResponseEntity<Map> respuesta = post(retraccion(id), anaSession, null, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("No se puede retractar dos veces de la misma clase")
    void noSePuedeDosVeces() {
        UUID id = bookAndPay(9);
        post(retraccion(id), anaSession, null, Map.class);

        ResponseEntity<Map> segunda = post(retraccion(id), anaSession, null, Map.class);

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refunds.findAll()).hasSize(1);
    }

    /**
     * 404 y no 403: confirmarle a otro estudiante que esa reserva existe ya sería decirle algo de
     * una persona que no es él. (Un profesor ni siquiera llega hasta aquí: el endpoint es de
     * estudiantes y Spring Security lo corta antes con un 403, que también es lo correcto.)
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("La clase de otro estudiante responde 404, no 403")
    void laClaseAjenaEs404() {
        UUID id = bookAndPay(9);
        createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        Session carlos = login("carlos@orion.test");

        assertThat(post(retraccion(id), carlos, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refunds.findAll()).isEmpty();
    }

    /**
     * La otra cara: una cancelación normal SÍ abona saldo, y ahora sola.
     *
     * <p>Los Términos ya prometían el valor completo como saldo al cancelar dentro de plazo, pero
     * el pago se quedaba esperando una decisión manual del admin. La decisión estaba tomada y
     * escrita en el contrato; lo único que faltaba era que el código la ejecutara.
     */
    @Test
    @DisplayName("Cancelar dentro de plazo abona saldo automáticamente, sin pasar por el admin")
    void cancelarAbonaSaldoSolo() {
        UUID id = bookAndPay(9);

        ResponseEntity<BookingResponse> respuesta = post(
                BOOKINGS + "/" + id + "/cancel", anaSession, null, BookingResponse.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(credits.findAll()).hasSize(1);
        assertThat(credits.findAll().getFirst().getAmountCop()).isEqualTo(RATE_COP);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        // Y no crea una devolución al medio de pago: el saldo y el retracto son caminos distintos.
        assertThat(refunds.findAll()).isEmpty();
    }

    /* --------------------------------------------------------------- la devolución */

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("La devolución aparece en la cola del admin con su plazo")
    void apareceEnLaColaDelAdmin() {
        bookAndPay(9);
        UUID id = bookings.findAll().getFirst().getId();
        post(retraccion(id), anaSession, null, Map.class);

        List<Map<String, Object>> cola =
                get("/api/v1/admin/refunds", adminSession, List.class).getBody();

        assertThat(cola).hasSize(1);
        assertThat(cola.getFirst()).containsEntry("status", "PENDING");
        assertThat(cola.getFirst()).containsEntry("studentName", "Ana Ramírez");
        // 15 días calendario desde hoy: quedan 14 completos y pico.
        assertThat(((Number) cola.getFirst().get("daysLeft")).intValue()).isBetween(14, 15);
    }

    /**
     * Sin la referencia de Wompi no se cierra. Es la misma regla de las liquidaciones: marcar como
     * pagado sin poder señalar el movimiento convierte el registro en una afirmación.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Confirmar sin referencia no se permite")
    void confirmarSinReferenciaNoSePermite() {
        UUID id = bookAndPay(9);
        post(retraccion(id), anaSession, null, Map.class);
        UUID refundId = refunds.findByBookingId(id).orElseThrow().getId();

        ResponseEntity<Map> respuesta = post("/api/v1/admin/refunds/" + refundId + "/confirm",
                adminSession, Map.of("reference", "  "), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Confirmar con referencia cierra la devolución y el pago a la vez")
    void confirmarCierraLasDosCosas() {
        UUID id = bookAndPay(9);
        post(retraccion(id), anaSession, null, Map.class);
        UUID refundId = refunds.findByBookingId(id).orElseThrow().getId();

        ResponseEntity<Map> respuesta = post("/api/v1/admin/refunds/" + refundId + "/confirm",
                adminSession, Map.of("reference", "WOMPI-REV-123", "note", "Devuelto a la tarjeta"),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refunds.findById(refundId).orElseThrow().getStatus())
                .isEqualTo(co.orion.billing.domain.RefundRequest.Status.PAID);
        assertThat(payments.findByBookingId(id).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        // Y sale de la cola: dejarla abierta haría que la siguiente persona la volviera a pagar.
        assertThat(get("/api/v1/admin/refunds", adminSession, List.class).getBody()).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un estudiante no entra a la cola de devoluciones")
    void laColaEsSoloDelAdmin() {
        assertThat(get("/api/v1/admin/refunds", anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
