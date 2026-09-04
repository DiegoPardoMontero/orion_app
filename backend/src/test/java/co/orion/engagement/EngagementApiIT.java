package co.orion.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
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
import co.orion.engagement.api.AchievementResponse;
import co.orion.engagement.api.CosmeticResponse;
import co.orion.engagement.api.EquipCosmeticsRequest;
import co.orion.engagement.api.MyEngagementResponse;
import co.orion.engagement.api.MyStreakResponse;
import co.orion.engagement.application.AchievementService;
import co.orion.engagement.persistence.PointEventRepository;
import co.orion.engagement.persistence.StreakProtectionRepository;
import co.orion.engagement.persistence.UserAchievementRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/** La API que lee y equipa. Lo delicado es que equipar valide en el servidor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, EngagementApiIT.FrozenClockConfiguration.class})
class EngagementApiIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-22T17:00:00Z");

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AchievementService motor;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private UserAchievementRepository userAchievements;

    @Autowired
    private PointEventRepository pointEvents;

    @Autowired
    private StreakProtectionRepository protections;

    private User ana;
    private User maria;
    private Session anaSession;
    private Session mariaSession;

    @BeforeEach
    void seed() {
        pointEvents.deleteAll();
        userAchievements.deleteAll();
        protections.deleteAll();
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ProfessorProfile perfil = new ProfessorProfile(maria);
        perfil.changeRate(45_000L);
        profiles.save(perfil);

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
    }

    private Booking claseTomada(LocalDate dia) {
        Booking booking = TestBookings.confirmed(ana.getId(), maria.getId(),
                ZonedDateTime.of(dia, LocalTime.of(18, 0), BusinessZone.BOGOTA).toInstant(),
                BookingModality.VIRTUAL, null, ana.getId());
        booking.autoComplete(FROZEN_NOW);
        return bookings.save(booking);
    }

    /* ---- Lectura ---- */

    @Test
    void unEstudianteSinNadaVeSuResumenEnCeroYNoUnError() {
        ResponseEntity<MyEngagementResponse> response =
                get("/api/v1/me/engagement", anaSession, MyEngagementResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().points()).isZero();
        assertThat(response.getBody().currentStreakWeeks()).isZero();
        // El sello nace en nivel 1 con solo registrarse.
        assertThat(response.getBody().sealLevel()).isEqualTo(1);
        assertThat(response.getBody().totalCount()).isEqualTo(20);
    }

    @Test
    void elResumenReflejaLoQueHaHecho() {
        claseTomada(LocalDate.of(2026, 7, 13));
        Booking segunda = claseTomada(LocalDate.of(2026, 7, 20));
        motor.onLessonCompleted(ana.getId(), segunda.getId(), FROZEN_NOW);

        var resumen = get("/api/v1/me/engagement", anaSession, MyEngagementResponse.class).getBody();

        assertThat(resumen.currentStreakWeeks()).isEqualTo(2);
        assertThat(resumen.unlockedCount()).isPositive();
        assertThat(resumen.points()).isPositive();
    }

    @Test
    void losVeinteLogrosLleganConSuProgreso() {
        ResponseEntity<AchievementResponse[]> response =
                get("/api/v1/me/achievements", anaSession, AchievementResponse[].class);

        assertThat(response.getBody()).hasSize(20);
        var primero = response.getBody()[0];
        assertThat(primero.target()).isPositive();
        assertThat(primero.glow()).isBetween(1, 3);
        assertThat(primero.family()).isNotBlank();
    }

    /** El estudiante tiene que leer «Con diez clases», no `volumen-10-clases`. */
    @Test
    void laCondicionDeUnCosmeticoLlegaEnTextoYNoComoCodigo() {
        ResponseEntity<CosmeticResponse[]> response =
                get("/api/v1/me/cosmetics", anaSession, CosmeticResponse[].class);

        assertThat(response.getBody()).isNotEmpty();
        for (CosmeticResponse pieza : response.getBody()) {
            assertThat(pieza.unlockCondition())
                    .as("condición de %s", pieza.code())
                    .isNotBlank()
                    .doesNotContain("volumen-", "constancia-", "primeros-");
        }
    }

    @Test
    void elMapaDeConstanciaTraeDoceSemanasPorDefecto() {
        ResponseEntity<MyStreakResponse> response =
                get("/api/v1/me/streak", anaSession, MyStreakResponse.class);

        assertThat(response.getBody().weeks()).hasSize(12);
        // La última es la semana en curso.
        assertThat(response.getBody().weeks().get(11).weekStart())
                .isEqualTo(LocalDate.of(2026, 7, 20));
    }

    /* ---- Equipar ---- */

    @Test
    void sePuedeEquiparUnaPiezaInicial() {
        ResponseEntity<Void> response = put("/api/v1/me/cosmetics", anaSession,
                new EquipCosmeticsRequest("trazo", "trazo", "crema", List.of()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * Confiar en que el frontend solo muestre lo desbloqueado es cómo alguien se pone la corona con
     * un `curl`. Esta es la comprobación que manda.
     */
    @SuppressWarnings("rawtypes")
    @Test
    void equiparUnaPiezaBloqueadaEs422() {
        ResponseEntity<Map> response = put("/api/v1/me/cosmetics", anaSession,
                new EquipCosmeticsRequest("cielo", "trazo", "crema", List.of()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    void equiparUnAccesorioBloqueadoTambienEs422() {
        ResponseEntity<Map> response = put("/api/v1/me/cosmetics", anaSession,
                new EquipCosmeticsRequest("trazo", "trazo", "crema",
                        List.of(new EquipCosmeticsRequest.Accessory("z3", "corona-constelacion"))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void unaPiezaDesbloqueadaSiSePuedeEquiparYPersiste() {
        Booking clase = claseTomada(LocalDate.of(2026, 7, 20));
        motor.onLessonCompleted(ana.getId(), clase.getId(), FROZEN_NOW);

        // «Órbita» se desbloquea con la primera clase.
        put("/api/v1/me/cosmetics", anaSession,
                new EquipCosmeticsRequest("orbita", "trazo", "crema", List.of()), Void.class);

        var cosmeticos = get("/api/v1/me/cosmetics", anaSession, CosmeticResponse[].class).getBody();
        assertThat(cosmeticos)
                .filteredOn(c -> c.code().equals("orbita") && c.kind().equals("FRAME"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.unlocked()).isTrue();
                    assertThat(c.equipped()).isTrue();
                });
    }

    /* ---- Quién puede ---- */

    @SuppressWarnings("rawtypes")
    @Test
    void unProfesorNoTieneGamificacion() {
        assertThat(get("/api/v1/me/engagement", mariaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/me/achievements", mariaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
