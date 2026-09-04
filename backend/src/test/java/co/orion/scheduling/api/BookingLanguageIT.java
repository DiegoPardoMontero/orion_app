package co.orion.scheduling.api;

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
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El idioma de la clase (Paso 0 del bloque 8). Es el dato que no se puede recuperar hacia atrás,
 * así que la regla importa: se asigna solo cuando es inequívoco y se exige cuando no lo es.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, BookingLanguageIT.FrozenClockConfiguration.class})
class BookingLanguageIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);

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
    private ProfessorLanguageRepository professorLanguages;

    private User soloIngles;
    private User bilingue;
    private Session anaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        professorLanguages.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        soloIngles = profesorPublicado("mono@orion.test", "María Gómez", "EN");
        bilingue = profesorPublicado("bi@orion.test", "Juan Torres", "EN", "FR");

        anaSession = login("ana@orion.test");
    }

    private User profesorPublicado(String email, String nombre, String... idiomas) {
        User profesor = createUser(email, nombre, UserRole.PROFESSOR);

        ProfessorProfile perfil = new ProfessorProfile(profesor);
        perfil.changeRate(0L);   // gratuita: la reserva se confirma sin pasarela
        perfil.publish();
        profiles.save(perfil);
        approveTeacher(profesor.getId());

        for (String idioma : idiomas) {
            professorLanguages.save(new ProfessorLanguage(profesor.getId(), idioma, false));
        }
        rules.save(new AvailabilityRule(profesor.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(18, 0)));
        return profesor;
    }

    private OffsetDateTime miercolesA(int hora) {
        return ZonedDateTime.of(WEDNESDAY, LocalTime.of(hora, 0), BusinessZone.BOGOTA)
                .toOffsetDateTime();
    }

    /** Un solo idioma: obligar a elegir entre una opción es un paso de más. */
    @Test
    void conUnProfesorDeUnIdiomaElIdiomaSeAsignaSolo() {
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(soloIngles.getId(), miercolesA(9), "VIRTUAL", null, null, null),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bookings.findById(response.getBody().id()).orElseThrow().getLanguageCode())
                .isEqualTo("EN");
    }

    /** Varios idiomas y ninguno elegido: deducirlo sería inventarlo. */
    @SuppressWarnings("rawtypes")
    @Test
    void conUnProfesorDeDosIdiomasSinElegirEs422() {
        ResponseEntity<Map> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(bilingue.getId(), miercolesA(10), "VIRTUAL", null, null, null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(bookings.count()).isZero();
    }

    @Test
    void conUnProfesorDeDosIdiomasEligiendoUnoValidoSeGuarda() {
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(bilingue.getId(), miercolesA(11), "VIRTUAL", null, "FR", null),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bookings.findById(response.getBody().id()).orElseThrow().getLanguageCode())
                .isEqualTo("FR");
    }

    /** Que el frontend solo ofrezca los suyos es cortesía; esta comprobación es la que manda. */
    @SuppressWarnings("rawtypes")
    @Test
    void unIdiomaQueEseProfesorNoEnsenaEs422() {
        ResponseEntity<Map> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(bilingue.getId(), miercolesA(12), "VIRTUAL", null, "ES", null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(bookings.count()).isZero();
    }

    /** El código llega en minúsculas desde algún cliente descuidado y aun así es el mismo idioma. */
    @Test
    void elCodigoSeNormalizaAMayusculas() {
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(bilingue.getId(), miercolesA(13), "VIRTUAL", null, "fr", null),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bookings.findById(response.getBody().id()).orElseThrow().getLanguageCode())
                .isEqualTo("FR");
    }

    /**
     * Una reserva histórica sin idioma —de un profesor multi-idioma, anterior a la V20— se puede
     * completar a mano. El backfill de la migración no llega a esas: representa "no lo sabemos",
     * y quien lo sabe es Pardo.
     */
    @Test
    void unaReservaSinIdiomaSePuedeCompletarDespues() {
        var historica = bookings.save(co.orion.scheduling.TestBookings.awaitingPayment(
                users.findAll().get(0).getId(), bilingue.getId(),
                miercolesA(14).toInstant(), miercolesA(15).toInstant(),
                co.orion.scheduling.domain.BookingModality.VIRTUAL, null, null,
                users.findAll().get(0).getId()));
        assertThat(historica.getLanguageCode()).isNull();

        historica.assignLanguage("FR");
        bookings.save(historica);

        assertThat(bookings.findById(historica.getId()).orElseThrow().getLanguageCode())
                .isEqualTo("FR");
    }
}
