package co.orion.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * El profe fundador de punta a punta: 15 % mientras dure su beneficio, que empieza con su primera
 * clase pagada y dura tres meses; 20 % para todos los demás y para él cuando termina. La comisión
 * se congela al reservar. El reloj se mueve para probar el final del beneficio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, FounderCommissionIT.RelojMovible.class})
class FounderCommissionIT extends ApiIntegrationSupport {

    /** Lunes 13/07/2026 a las 12:00 de Bogotá. */
    private static final Instant INICIO = Instant.parse("2026-07-13T17:00:00Z");
    /** Tres meses después, a las 00:00 de Bogotá. */
    private static final Instant FIN = Instant.parse("2026-10-13T05:00:00Z");
    private static final AtomicReference<Instant> AHORA = new AtomicReference<>(INICIO);
    private static final long TARIFA = 60_000;

    @TestConfiguration
    static class RelojMovible {
        @Bean
        @Primary
        Clock relojMovible() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return AHORA.get();
                }
            };
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

    @Autowired
    private JdbcTemplate jdbc;

    private User maria;
    private User juan;
    private Session anaSession;
    private Session carlosSession;

    @BeforeEach
    void seed() {
        AHORA.set(INICIO);
        jdbc.update("update platform_settings set value = '2000' where key = 'commission_rate_bps'");
        jdbc.update("update platform_settings set value = '1500' where key = 'founder_commission_rate_bps'");
        jdbc.update("update platform_settings set value = '3' where key = 'founder_period_months'");

        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = profe("maria@orion.test", "María Gómez", true);
        juan = profe("juan@orion.test", "Juan Torres", false);

        anaSession = login("ana@orion.test");
        carlosSession = login("carlos@orion.test");
    }

    private User profe(String email, String nombre, boolean fundador) {
        User profe = createUser(email, nombre, UserRole.PROFESSOR);
        ProfessorProfile perfil = new ProfessorProfile(profe);
        perfil.changeRate(TARIFA);
        perfil.publish();
        if (fundador) {
            perfil.grantFounder(1500, 3, INICIO);
        }
        profiles.save(perfil);
        approveTeacher(profe.getId());
        rules.save(new AvailabilityRule(profe.getId(), DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        return profe;
    }

    /** Reserva el miércoles siguiente a «ahora», a la hora dada. */
    private UUID reservar(Session sesion, User profe, int hora, boolean prueba) {
        LocalDate miercoles = AHORA.get().atZone(BusinessZone.BOGOTA).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        OffsetDateTime at = ZonedDateTime.of(miercoles, LocalTime.of(hora, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> r = post("/api/v1/bookings", sesion,
                new CreateBookingRequest(profe.getId(), at, "VIRTUAL", null, null, null, prueba), BookingResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private int comisionDe(UUID reserva) {
        return payments.findByBookingId(reserva).orElseThrow().getCommissionRateBps();
    }

    private Instant empezo(User profe) {
        Timestamp t = jdbc.queryForObject("select founder_started_at from professor_profiles where user_id = ?",
                Timestamp.class, profe.getId());
        return t == null ? null : t.toInstant();
    }

    private Instant termina(User profe) {
        Timestamp t = jdbc.queryForObject("select founder_until from professor_profiles where user_id = ?",
                Timestamp.class, profe.getId());
        return t == null ? null : t.toInstant();
    }

    @Test
    void elFundadorReservaAl15YSuPrimerPagoArrancaElConteo() {
        UUID reserva = reservar(anaSession, maria, 9, false);

        assertThat(comisionDe(reserva)).isEqualTo(1500);
        assertThat(payments.findByBookingId(reserva).orElseThrow().getCommissionCop()).isEqualTo(9_000);
        // Reservar no arranca nada: arranca la primera clase pagada.
        assertThat(empezo(maria)).isNull();

        approvePayment(reserva);

        assertThat(empezo(maria)).isEqualTo(INICIO);
        assertThat(termina(maria)).isEqualTo(FIN);
    }

    @Test
    void unaReservaCreadaDentroDelPeriodoVaAl15YUnaCreadaDespuesAl20() {
        approvePayment(reservar(anaSession, maria, 9, false));

        AHORA.set(FIN.minusSeconds(3600));
        UUID dentro = reservar(anaSession, maria, 9, false);
        AHORA.set(FIN.plusSeconds(3600));
        UUID despues = reservar(carlosSession, maria, 10, false);

        assertThat(comisionDe(dentro)).isEqualTo(1500);
        assertThat(comisionDe(despues)).isEqualTo(2000);
    }

    @Test
    void cambiarLosAjustesNoTocaAQuienYaEsFundador() {
        jdbc.update("update platform_settings set value = '1000' where key = 'founder_commission_rate_bps'");
        jdbc.update("update platform_settings set value = '1' where key = 'founder_period_months'");

        UUID reserva = reservar(anaSession, maria, 9, false);
        approvePayment(reserva);

        assertThat(comisionDe(reserva)).isEqualTo(1500);
        assertThat(termina(maria)).isEqualTo(FIN);
    }

    @Test
    void laClaseDePruebaLlevaLaComisionDeFundadorYTambienArrancaElConteo() {
        jdbc.update("update professor_profiles set accepts_trial = true where user_id = ?", maria.getId());

        UUID prueba = reservar(anaSession, maria, 9, true);

        assertThat(comisionDe(prueba)).isEqualTo(1500);
        assertThat(payments.findByBookingId(prueba).orElseThrow().getCommissionCop()).isZero();
        assertThat(empezo(maria)).isEqualTo(INICIO);
    }

    @Test
    void unProfeQueNoEsFundadorVaAl20() {
        UUID reserva = reservar(anaSession, juan, 9, false);
        approvePayment(reserva);

        assertThat(comisionDe(reserva)).isEqualTo(2000);
        assertThat(empezo(juan)).isNull();
    }

    @Test
    void cancelarLaPrimeraClaseNoReiniciaElConteo() {
        UUID primera = reservar(anaSession, maria, 9, false);
        approvePayment(primera);
        assertThat(post("/api/v1/bookings/" + primera + "/cancel", anaSession, null, BookingResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        AHORA.set(INICIO.plusSeconds(10 * 86_400L));
        approvePayment(reservar(carlosSession, maria, 10, false));

        assertThat(empezo(maria)).isEqualTo(INICIO);
        assertThat(termina(maria)).isEqualTo(FIN);
    }
}
