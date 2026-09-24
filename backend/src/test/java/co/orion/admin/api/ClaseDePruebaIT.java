package co.orion.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.UserRole;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La clase de prueba del administrador: ensayar el aula sin pasar por la pasarela.
 *
 * <p>Lo que de verdad se fija aquí es lo que NO hace. Una prueba que dejara un pago, un correo o
 * una fila en las ganancias del profesor sería peor que no poder probar: ensuciaría la contabilidad
 * de alguien para ahorrarnos un rato.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ClaseDePruebaIT extends ApiIntegrationSupport {

    private static final String RUTA = "/api/v1/admin/system/test-class";

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private PaymentRepository payments;

    private Session admin;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        users.deleteAll();
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        admin = login("admin@orion.test");
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> crear(String estudiante, String profesor, LocalDateTime cuando) {
        return post(RUTA, admin,
                Map.of("studentEmail", estudiante, "professorEmail", profesor,
                        "startsAt", cuando.toString()),
                Map.class);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Nace confirmada, con aula, y sin haber pasado por ningún pago")
    void naceConfirmadaYSinPago() {
        LocalDateTime cuando = LocalDateTime.of(2026, 9, 15, 20, 0);

        ResponseEntity<Map> r = crear("ana@orion.test", "maria@orion.test", cuando);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString((String) r.getBody().get("bookingId"));

        var reserva = bookings.findById(id).orElseThrow();
        assertThat(reserva.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reserva.isRehearsal()).isTrue();
        // El aula, ya asignada: es el enlace que el admin va a abrir.
        assertThat(reserva.getMeetingLink()).isEqualTo("/mis-clases/" + id + "/aula");
        // Y lo importante: ni un peso. Sin fila en payments no hay nada que liquidar.
        assertThat(payments.findByBookingId(id)).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Dura lo que dura una clase, no una hora redonda")
    void duraCincuentaYCinco() {
        LocalDateTime cuando = LocalDateTime.of(2026, 9, 15, 20, 0);

        ResponseEntity<Map> r = crear("ana@orion.test", "maria@orion.test", cuando);
        var reserva = bookings.findById(
                UUID.fromString((String) r.getBody().get("bookingId"))).orElseThrow();

        assertThat(reserva.getEndsAt()).isEqualTo(reserva.getStartsAt().plusSeconds(55 * 60));
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una cuenta que no existe se dice con su nombre, no con un 500")
    void elCorreoQueNoExisteSeExplica() {
        ResponseEntity<Map> r = crear("nadie@orion.test", "maria@orion.test",
                LocalDateTime.of(2026, 9, 15, 20, 0));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) r.getBody().get("error")).contains("nadie@orion.test");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un estudiante no puede hacer de profesor en la prueba")
    void elProfesorTieneQueSerProfesor() {
        ResponseEntity<Map> r = crear("ana@orion.test", "admin@orion.test",
                LocalDateTime.of(2026, 9, 15, 20, 0));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) r.getBody().get("error")).contains("PROFESSOR");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("La misma cuenta a los dos lados no sirve para probar quién modera")
    void laMismaCuentaDosVecesNoSirve() {
        ResponseEntity<Map> r = crear("maria@orion.test", "maria@orion.test",
                LocalDateTime.of(2026, 9, 15, 20, 0));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("No pisa una clase real: la constraint manda igual que con una reserva de verdad")
    void noPisaUnaClaseDeVerdad() {
        LocalDateTime cuando = LocalDateTime.of(2026, 9, 15, 20, 0);
        assertThat(crear("ana@orion.test", "maria@orion.test", cuando).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Segunda a la misma hora con el mismo profesor: el índice único parcial la rechaza.
        assertThat(crear("ana@orion.test", "maria@orion.test", cuando).getStatusCode())
                .isIn(HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Solo el administrador puede crearla")
    void soloElAdmin() {
        Session estudiante = login("ana@orion.test");
        ResponseEntity<Map> r = post(RUTA, estudiante,
                Map.of("studentEmail", "ana@orion.test", "professorEmail", "maria@orion.test",
                        "startsAt", LocalDateTime.of(2026, 9, 15, 20, 0).toString()),
                Map.class);

        assertThat(r.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }
}
