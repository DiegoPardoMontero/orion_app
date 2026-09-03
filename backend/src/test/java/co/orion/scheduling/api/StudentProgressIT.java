package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El panel del estudiante de punta a punta. El reloj va congelado el lunes 20 de julio de 2026 a
 * mediodía en Bogotá, así que las rachas son deterministas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, StudentProgressIT.FrozenClockConfiguration.class})
class StudentProgressIT extends ApiIntegrationSupport {

    private static final String PROGRESS = "/api/v1/me/progress";
    /** Lunes 20 de julio de 2026, 12:00 en Bogotá. */
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-20T17:00:00Z");

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
    private ProfessorProfileRepository profiles;

    private User ana;
    private User maria;
    private User juan;
    private Session anaSession;
    private Session mariaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);

        ProfessorProfile ficha = new ProfessorProfile(maria);
        ficha.describe("Profesora de inglés conversacional para adultos",
                "Enseño inglés conversacional a adultos que ya estudiaron el idioma alguna vez y "
                        + "aun así no se atreven a hablarlo. Practicamos desde la primera clase.");
        profiles.save(ficha);
        profiles.save(new ProfessorProfile(juan));

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
    }

    private Instant enBogota(LocalDate dia, int hora) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, 0), BusinessZone.BOGOTA).toInstant();
    }

    private void claseTomada(User profesor, LocalDate dia) {
        Booking booking = TestBookings.confirmed(ana.getId(), profesor.getId(), enBogota(dia, 18),
                BookingModality.VIRTUAL, null, ana.getId());
        booking.autoComplete(FROZEN_NOW);
        bookings.save(booking);
    }

    private MyProgressResponse progreso() {
        ResponseEntity<MyProgressResponse> response = get(PROGRESS, anaSession, MyProgressResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void unEstudianteSinClasesVeSuPanelEnCeroYNoUnError() {
        MyProgressResponse progreso = progreso();

        assertThat(progreso.lessonsTaken()).isZero();
        assertThat(progreso.minutesTotal()).isZero();
        assertThat(progreso.currentStreakWeeks()).isZero();
        assertThat(progreso.nextLesson()).isNull();
        assertThat(progreso.professors()).isEmpty();
        assertThat(progreso.lessonsByDay()).isEmpty();
        // El mapa necesita su rango aunque no haya nada que pintar.
        assertThat(progreso.mapFrom()).isNotNull();
        assertThat(progreso.today()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void cuentaLasClasesTomadasYSusMinutos() {
        claseTomada(maria, LocalDate.of(2026, 7, 6));
        claseTomada(maria, LocalDate.of(2026, 7, 13));

        MyProgressResponse progreso = progreso();

        assertThat(progreso.lessonsTaken()).isEqualTo(2);
        assertThat(progreso.minutesTotal()).isEqualTo(120);
    }

    @Test
    void tresSemanasSeguidasSonUnaRachaDeTres() {
        claseTomada(maria, LocalDate.of(2026, 7, 6));
        claseTomada(maria, LocalDate.of(2026, 7, 13));
        claseTomada(maria, LocalDate.of(2026, 7, 20));

        MyProgressResponse progreso = progreso();

        assertThat(progreso.currentStreakWeeks()).isEqualTo(3);
        assertThat(progreso.bestStreakWeeks()).isEqualTo(3);
    }

    /** Una clase cancelada no se tomó: ni cuenta, ni sostiene una racha. */
    @Test
    void lasCanceladasNoCuentan() {
        Booking cancelada = TestBookings.confirmed(ana.getId(), maria.getId(),
                enBogota(LocalDate.of(2026, 7, 13), 18), BookingModality.VIRTUAL, null, ana.getId());
        cancelada.cancel(BookingStatus.CANCELLED_BY_STUDENT, ana.getId(),
                enBogota(LocalDate.of(2026, 7, 12), 10), "Imprevisto");
        bookings.save(cancelada);

        MyProgressResponse progreso = progreso();

        assertThat(progreso.lessonsTaken()).isZero();
        assertThat(progreso.currentStreakWeeks()).isZero();
    }

    @Test
    void laProximaClaseEsLaConfirmadaMasCercana() {
        bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(),
                enBogota(LocalDate.of(2026, 7, 24), 18), BookingModality.VIRTUAL, null, ana.getId()));
        bookings.save(TestBookings.confirmed(ana.getId(), juan.getId(),
                enBogota(LocalDate.of(2026, 7, 22), 18), BookingModality.VIRTUAL, null, ana.getId()));

        MyProgressResponse progreso = progreso();

        assertThat(progreso.nextLesson()).isNotNull();
        assertThat(progreso.nextLesson().professorName()).isEqualTo("Juan Torres");
        assertThat(progreso.nextLesson().startsAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 22));
    }

    /**
     * Una reserva sin pagar todavía no es una clase: anunciarla con su cuenta atrás sería prometer
     * algo que puede vencer en veinte minutos.
     */
    @Test
    void unaReservaSinPagarNoEsLaProximaClase() {
        bookings.save(TestBookings.awaitingPayment(ana.getId(), maria.getId(),
                enBogota(LocalDate.of(2026, 7, 22), 18), enBogota(LocalDate.of(2026, 7, 22), 19),
                BookingModality.VIRTUAL, null, ana.getId()));

        assertThat(progreso().nextLesson()).isNull();
    }

    @Test
    void losProfesoresVanDeQuienMasClasesLeDioAlQueMenos() {
        claseTomada(juan, LocalDate.of(2026, 7, 6));
        claseTomada(maria, LocalDate.of(2026, 7, 7));
        claseTomada(maria, LocalDate.of(2026, 7, 13));

        MyProgressResponse progreso = progreso();

        assertThat(progreso.professors()).hasSize(2);
        assertThat(progreso.professors().get(0).fullName()).isEqualTo("María Gómez");
        assertThat(progreso.professors().get(0).lessons()).isEqualTo(2);
        assertThat(progreso.professors().get(0).headline())
                .isEqualTo("Profesora de inglés conversacional para adultos");
        assertThat(progreso.professors().get(1).fullName()).isEqualTo("Juan Torres");
    }

    @Test
    void elMapaAgrupaLasClasesPorDia() {
        claseTomada(maria, LocalDate.of(2026, 7, 13));
        claseTomada(juan, LocalDate.of(2026, 7, 13));
        claseTomada(maria, LocalDate.of(2026, 7, 14));

        MyProgressResponse progreso = progreso();

        assertThat(progreso.lessonsByDay())
                .containsEntry(LocalDate.of(2026, 7, 13), 2)
                .containsEntry(LocalDate.of(2026, 7, 14), 1);
    }

    /** El panel mide clases tomadas, no dictadas: no es del profesor. */
    @Test
    void unProfesorNoTienePanelDeProgreso() {
        ResponseEntity<String> response = get(PROGRESS, mariaSession, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
