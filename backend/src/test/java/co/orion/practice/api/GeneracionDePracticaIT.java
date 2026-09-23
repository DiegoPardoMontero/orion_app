package co.orion.practice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.practice.application.GeneradorSinIa;
import co.orion.practice.application.Material;
import co.orion.practice.application.PracticeAiBudget;
import co.orion.practice.application.PracticeGenerator;
import co.orion.practice.application.PracticeGenerator.Generado;
import co.orion.practice.application.PracticeService;
import co.orion.practice.domain.PracticeItemType;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La generación diferida con un generador de mentira, para decidir exactamente qué devuelve.
 *
 * <p>Lo que se ofrece: con menos de dos ejercicios anclados al acta el set falla al tercer intento y
 * al estudiante no le llega nada; con dos, sale (brief, C2). Y una caída del proveedor no le cuesta
 * la práctica a nadie: el trabajo corre cada minuto, así que si cada timeout contara como intento,
 * tres minutos de OpenAI caído dejarían FAILED todos los sets pendientes por algo que no es culpa
 * del acta. Cada llamada queda en el registro de gasto aunque la generación del set haga rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class GeneracionDePracticaIT extends ApiIntegrationSupport {

    private static final String NOTAS =
            "Trabajamos past simple; sigue diciendo 'I go yesterday' y le costó 'used to'. Próxima: condicionales.";

    @MockitoBean
    private PracticeGenerator generador;

    @Autowired
    private PracticeAiBudget presupuesto;

    @Autowired
    private PracticeService practica;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    private User maria;
    private User ana;

    @BeforeEach
    void seed() {
        limpiar();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from ai_usage_log where feature = 'practice'");
        jdbc.update("delete from practice_sets");
        jdbc.update("delete from lesson_notes");
        bookings.deleteAll();
    }

    @SuppressWarnings("rawtypes")
    private void actaPublicada() {
        UUID clase = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId())).getId();
        jdbc.update("update bookings set status = 'COMPLETED', completed_at = now() where id = ?", clase);
        Session sesionMaria = login("maria@orion.test");
        Map borrador = post("/api/v1/bookings/" + clase + "/lesson-note/draft", sesionMaria,
                Map.of("rawInput", NOTAS), Map.class).getBody();
        post("/api/v1/lesson-notes/" + borrador.get("id") + "/publish", sesionMaria, null, Map.class);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                jdbc.queryForObject("select count(*) from practice_sets", Integer.class) == 1);
    }

    /** Lo que el generador sin IA saca de estas notas, que el validador acepta entero. */
    private static List<Generado> anclados(InvocationOnMock inv) {
        return new GeneradorSinIa().generar(inv.getArgument(0), inv.<Material>getArgument(1), inv.getArgument(2));
    }

    /** Un término que el acta no tiene: el validador lo descarta. */
    private static final Generado SIN_ANCLA = new Generado(PracticeItemType.FILL_BLANK, "Completa.",
            "{\"sentence\":\"I'm ___ travel.\",\"options\":[\"gonna\",\"going\"]}", "gonna", "Futuro.", "gonna");

    @Test
    @DisplayName("Un solo ejercicio anclado no alcanza: el set falla al tercer intento y no se ofrece")
    void unoNoAlcanza() {
        when(generador.disponible()).thenReturn(true);
        when(generador.generar(any(), any(), anyInt()))
                .thenAnswer(inv -> List.of(anclados(inv).getFirst(), SIN_ANCLA));
        actaPublicada();

        for (int corrida = 0; corrida < 3; corrida++) {
            practica.generarPendientes();
        }

        assertThat(jdbc.queryForObject("select status from practice_sets", String.class)).isEqualTo("FAILED");
        assertThat(get("/api/v1/me/practice", login("ana@orion.test"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("Con dos anclados, sale: el set queda listo con esos dos")
    void dosAlcanzan() {
        when(generador.disponible()).thenReturn(true);
        when(generador.generar(any(), any(), anyInt()))
                .thenAnswer(inv -> List.of(anclados(inv).get(0), SIN_ANCLA, anclados(inv).get(1)));
        actaPublicada();

        assertThat(practica.generarPendientes()).isEqualTo(1);

        assertThat(jdbc.queryForMap("select status, item_count::int as item_count from practice_sets"))
                .containsEntry("status", "READY").containsEntry("item_count", 2);
    }

    @Test
    @DisplayName("Cinco corridas con el proveedor caído: el set sigue pendiente, sin gastar intentos; al volver, sale")
    void unaCaidaNoLeCuestaLaPracticaANadie() {
        AtomicInteger llamadas = new AtomicInteger();
        when(generador.disponible()).thenReturn(true);
        when(generador.generar(any(), any(), anyInt())).thenAnswer(inv -> {
            if (llamadas.incrementAndGet() <= 5) {
                // Como el cliente de verdad: primero su fila, después «no respondió».
                presupuesto.registrar(inv.getArgument(0), "gpt-5-mini", null, null, 60_000, "TIMEOUT");
                throw new PracticeGenerator.ProveedorNoRespondio("se agotó el tiempo");
            }
            return anclados(inv);
        });
        actaPublicada();

        for (int corrida = 0; corrida < 5; corrida++) {
            assertThat(practica.generarPendientes()).isZero();
        }
        assertThat(jdbc.queryForMap("select status, generation_attempts::int as generation_attempts from practice_sets"))
                .containsEntry("status", "PENDING").containsEntry("generation_attempts", 0);
        // El set hizo rollback cinco veces; lo gastado, no.
        assertThat(jdbc.queryForObject(
                "select count(*) from ai_usage_log where feature = 'practice' and outcome = 'TIMEOUT'",
                Integer.class)).isEqualTo(5);

        assertThat(practica.generarPendientes()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from practice_sets", String.class)).isEqualTo("READY");
    }
}
