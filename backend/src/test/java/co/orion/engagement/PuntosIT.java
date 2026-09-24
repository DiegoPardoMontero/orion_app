package co.orion.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.assessment.domain.AssessmentCompletedEvent;
import co.orion.engagement.api.MyPointsResponse;
import co.orion.engagement.application.AchievementService;
import co.orion.engagement.persistence.PointEventRepository;
import co.orion.engagement.persistence.UserAchievementRepository;
import co.orion.identity.api.StudentVisibilityRequest;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.messaging.api.ConversationSummaryResponse;
import co.orion.messaging.api.CreateConversationRequest;
import co.orion.messaging.api.SendMessageRequest;
import co.orion.reputation.api.CreateReviewRequest;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.RoomParticipations;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;

/**
 * Los puntos por todas partes (24/09/2026): las maneras nuevas de hacerlos, que ninguna se pueda
 * repetir para inflarlos, y quién puede ver los de quién.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class PuntosIT extends ApiIntegrationSupport {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AchievementService motor;

    @Autowired
    private PointEventRepository pointEvents;

    @Autowired
    private UserAchievementRepository userAchievements;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private RoomParticipations salas;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transaccion;

    private User ana;
    private User maria;
    private User juan;
    private Session anaSession;
    private Session mariaSession;
    private Session juanSession;

    @BeforeEach
    void seed() {
        pointEvents.deleteAll();
        userAchievements.deleteAll();
        jdbc.update("delete from onboarding_steps");
        jdbc.update("delete from reviews");
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Pérez", UserRole.PROFESSOR);
        for (User profe : List.of(maria, juan)) {
            ProfessorProfile perfil = new ProfessorProfile(profe);
            perfil.changeRate(45_000L);
            perfil.publish();
            profiles.save(perfil);
            approveTeacher(profe.getId());
        }
        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
        juanSession = login("juan@orion.test");

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    private int puntosDe(String fuente) {
        return jdbc.queryForObject(
                "select coalesce(sum(points), 0) from point_events where user_id = ? and source_type = ?",
                Integer.class, ana.getId(), fuente);
    }

    private void escribirle(User profe, String texto) {
        UUID hilo = post("/api/v1/conversations", anaSession, new CreateConversationRequest(profe.getId()),
                ConversationSummaryResponse.class).getBody().id();
        assertThat(post("/api/v1/conversations/" + hilo + "/messages", anaSession, new SendMessageRequest(texto),
                Map.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("Escribirle a un profe da puntos una vez por profe, no por mensaje, y enciende «Primer mensaje»")
    void elPrimerMensajeACadaProfe() {
        escribirle(maria, "Hola, ¿tienes cupo el martes?");
        escribirle(maria, "¿Y el jueves?");
        await().atMost(Duration.ofSeconds(5)).until(() -> puntosDe("MESSAGE") == 5);

        escribirle(juan, "Hola, Juan.");
        await().atMost(Duration.ofSeconds(5)).until(() -> puntosDe("MESSAGE") == 10);

        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("""
                select count(*) from user_achievements
                where user_id = ? and achievement_code = 'primeros-primer-mensaje' and unlocked_at is not null
                """, Integer.class, ana.getId()) == 1);
    }

    @Test
    @DisplayName("Hacer visible la ficha da 10 una vez: apagarla y encenderla otra vez no suma")
    void laFichaVisibleUnaVez() {
        put("/api/v1/me/student-profile/visibility", anaSession, new StudentVisibilityRequest(true), Map.class);
        await().atMost(Duration.ofSeconds(5)).until(() -> puntosDe("PROFILE_PUBLIC") == 10);

        put("/api/v1/me/student-profile/visibility", anaSession, new StudentVisibilityRequest(false), Map.class);
        put("/api/v1/me/student-profile/visibility", anaSession, new StudentVisibilityRequest(true), Map.class);
        motor.onSomethingHappened(ana.getId());
        assertThat(puntosDe("PROFILE_PUBLIC")).isEqualTo(10);
    }

    @Test
    @DisplayName("Terminar el recorrido da 10 una vez; el diagnóstico con Meissa, 20 una vez")
    void recorridoYDiagnostico() {
        assertThat(post("/api/v1/me/onboarding/TOUR_STUDENT", anaSession, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        post("/api/v1/me/onboarding/TOUR_STUDENT", anaSession, null, Void.class);
        await().atMost(Duration.ofSeconds(5)).until(() -> puntosDe("TOUR") == 10);

        jdbc.update("""
                insert into confidence_assessments (user_id, language_code, status, score, completed_at)
                values (?, 'EN', 'COMPLETED', 72, now())
                """, ana.getId());
        transaccion.executeWithoutResult(t -> events.publishEvent(new AssessmentCompletedEvent(ana.getId())));
        transaccion.executeWithoutResult(t -> events.publishEvent(new AssessmentCompletedEvent(ana.getId())));
        await().atMost(Duration.ofSeconds(5)).until(() -> puntosDe("DIAGNOSTIC") == 20);
        assertThat(puntosDe("DIAGNOSTIC")).isEqualTo(20);
    }

    @Test
    @DisplayName("Entrar al aula a tiempo suma 5 a la clase; llegar diez minutos tarde, no")
    void laPuntualidad() {
        Instant hace2Dias = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Booking aTiempo = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(), hace2Dias,
                BookingModality.VIRTUAL, null, ana.getId()));
        Booking tarde = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(), hace2Dias.plus(3, ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId()));
        salas.entro(aTiempo.getId(), ana.getId(), aTiempo.getStartsAt().plus(4, ChronoUnit.MINUTES));
        salas.entro(tarde.getId(), ana.getId(), tarde.getStartsAt().plus(10, ChronoUnit.MINUTES));

        motor.onLessonCompleted(ana.getId(), aTiempo.getId(), Instant.now());
        motor.onLessonCompleted(ana.getId(), tarde.getId(), Instant.now());
        motor.onLessonCompleted(ana.getId(), aTiempo.getId(), Instant.now());

        assertThat(puntosDe("LESSON")).isEqualTo(50);
        assertThat(puntosDe("PUNCTUAL")).isEqualTo(5);
    }

    @Test
    @DisplayName("Calificar la clase da 20 y enciende «Primera reseña» en el acto (antes no daba nada)")
    void calificarDaPuntos() {
        Booking clase = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId()));
        assertThat(post("/api/v1/bookings/" + clase.getId() + "/review", anaSession,
                new CreateReviewRequest((short) 5, "Muy clara."), Map.class).getStatusCode().is2xxSuccessful()).isTrue();

        await().atMost(Duration.ofSeconds(5)).until(() -> puntosDe("REVIEW") == 20);
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("""
                select count(*) from user_achievements
                where user_id = ? and achievement_code = 'compromiso-primera-resena' and unlocked_at is not null
                """, Integer.class, ana.getId()) == 1);
    }

    @Test
    @DisplayName("«Tus puntos» trae el total, lo último con quién fue y las maneras de hacer más")
    void misPuntos() {
        Booking clase = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId()));
        motor.onLessonCompleted(ana.getId(), clase.getId(), Instant.now());

        MyPointsResponse puntos = get("/api/v1/me/points", anaSession, MyPointsResponse.class).getBody();
        assertThat(puntos.total()).isEqualTo(jdbc.queryForObject(
                "select sum(points) from point_events where user_id = ?", Long.class, ana.getId()));
        assertThat(puntos.recent()).anySatisfy(m -> {
            assertThat(m.source()).isEqualTo("LESSON");
            assertThat(m.detail()).isEqualTo("María");
        });
        assertThat(puntos.recent()).anySatisfy(m -> {
            assertThat(m.source()).isEqualTo("ACHIEVEMENT");
            assertThat(m.detail()).isEqualTo("Primera clase");
        });
        assertThat(puntos.ways()).extracting(MyPointsResponse.Way::source)
                .contains("LESSON", "MESSAGE", "PROFILE_PUBLIC", "TOUR", "DIAGNOSTIC")
                .doesNotContain("ACHIEVEMENT");

        assertThat(get("/api/v1/me/points", mariaSession, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Los puntos de otro se ven donde se ve su ficha: su profe sí, un profe ajeno no")
    void quienVeLosPuntosDeQuien() {
        bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId()));
        String ruta = "/api/v1/students/" + ana.getId() + "/points";

        assertThat(get(ruta, mariaSession, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(ruta, juanSession, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(ruta, anaSession, Map.class).getBody()).containsKey("total");
    }
}
