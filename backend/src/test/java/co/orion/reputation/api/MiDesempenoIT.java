package co.orion.reputation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.reputation.application.MetricsRecalculationJob;
import co.orion.reputation.application.SanctionService;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * «Mi desempeño» del profesor: vacío antes del primer cálculo, con sus cifras después del recálculo
 * nocturno y con las sanciones a la vista. El recálculo entra por {@code nightly()}, el mismo método
 * que llama el programador de tareas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class MiDesempenoIT extends ApiIntegrationSupport {

    private static final String RUTA = "/api/v1/me/performance";

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private MetricsRecalculationJob recalculo;

    @Autowired
    private SanctionService sanciones;

    @Autowired
    private JdbcTemplate jdbc;

    private User maria;
    private User ana;
    private User admin;
    private Session sesionMaria;

    /** Correos nuevos en cada prueba: no depende de lo que otras dejen en la base. */
    @BeforeEach
    void seed() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        maria = createUser("maria." + sufijo + "@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana." + sufijo + "@orion.test", "Ana Ramírez", UserRole.STUDENT);
        admin = createUser("admin." + sufijo + "@orion.test", "Admin", UserRole.ADMIN);
        ProfessorProfile perfil = new ProfessorProfile(maria);
        perfil.changeRate(50_000L);
        perfil.publish();
        profiles.save(perfil);
        approveTeacher(maria.getId());
        sesionMaria = login(maria.getEmail());
    }

    /**
     * Lo que esta prueba deja lo borra ella: las demás del mismo contexto limpian con
     * {@code users.deleteAll()}, y una reserva o una sanción sin borrar les tumba la FK.
     */
    @AfterEach
    void limpiar() {
        jdbc.update("delete from professor_sanctions where professor_id = ?", maria.getId());
        jdbc.update("delete from bookings where professor_id = ?", maria.getId());
    }

    /** Una clase dictada de María con Ana, hace unos días. */
    private void claseDictada(int diasAtras) {
        Instant empezo = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(diasAtras));
        UUID id = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(), empezo,
                BookingModality.VIRTUAL, null, ana.getId())).getId();
        jdbc.update("update bookings set status = 'COMPLETED' where id = ?", id);
    }

    @Test
    @DisplayName("Antes del primer cálculo no hay nada que medir: cifras vacías, no un error")
    void sinCalculoEstaVacio() {
        PerformanceResponse yo = get(RUTA, sesionMaria, PerformanceResponse.class).getBody();

        assertThat(yo.lessonsCompleted()).isZero();
        assertThat(yo.computedAt()).isNull();
        assertThat(yo.sanctions()).isEmpty();
    }

    @Test
    @DisplayName("Tras el recálculo nocturno, el profesor ve sus clases dictadas y la ventana que se midió")
    void elRecalculoLlenaLasCifras() {
        claseDictada(2);
        claseDictada(5);

        recalculo.nightly();

        PerformanceResponse yo = get(RUTA, sesionMaria, PerformanceResponse.class).getBody();
        assertThat(yo.lessonsCompleted()).isEqualTo(2);
        assertThat(yo.computedAt()).isNotNull();
        assertThat(yo.windowDays()).isEqualTo((short) 90);
        assertThat(yo.activeStudents()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sus sanciones, con el motivo, las ve él mismo: una sanción invisible no se entiende")
    void veSusSanciones() {
        sanciones.applyManually(maria.getId(), "WARNING", "Llegó tarde tres veces.", admin.getId());

        PerformanceResponse yo = get(RUTA, sesionMaria, PerformanceResponse.class).getBody();

        assertThat(yo.sanctions()).singleElement().satisfies(s -> {
            assertThat(s.type()).isEqualTo("WARNING");
            assertThat(s.reason()).isEqualTo("Llegó tarde tres veces.");
        });
    }
}
