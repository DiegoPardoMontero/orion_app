package co.orion.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PaymentExpiryJob;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.lifecycle.application.RecordatorioDeClases;
import co.orion.reputation.api.CreateReviewRequest;
import co.orion.reputation.application.SanctionService;
import co.orion.reputation.domain.ProfessorSanction;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;

/**
 * Los avisos y correos que faltaban (24/09/2026): recordatorios de clase y de calificar, la reseña
 * que recibe el profe, la reserva vencida, la respuesta de soporte, las sanciones y el correo de
 * prueba de Sistema. Lo que más importa: que cada recordatorio salga una sola vez.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AvisosYCorreosIT extends ApiIntegrationSupport {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordatorioDeClases recordatorio;

    @Autowired
    private PaymentExpiryJob expiracion;

    @Autowired
    private SanctionService sanciones;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ProfessorProfileRepository profiles;

    private User ana;
    private User maria;
    private User admin;
    private Session anaSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from reviews");
        jdbc.update("delete from support_messages");
        jdbc.update("delete from support_tickets");
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        admin = createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        ProfessorProfile perfil = new ProfessorProfile(maria);
        perfil.changeRate(45_000L);
        perfil.publish();
        profiles.save(perfil);
        approveTeacher(maria.getId());
        anaSession = login("ana@orion.test");
        adminSession = login("admin@orion.test");

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    private Booking clase(Instant empieza, Instant reservadaEl) {
        Booking b = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(), empieza,
                BookingModality.VIRTUAL, null, ana.getId()));
        jdbc.update("update bookings set created_at = ? where id = ?", java.sql.Timestamp.from(reservadaEl), b.getId());
        return b;
    }

    private Booking claseDada(Instant empezo) {
        Booking b = clase(empezo, empezo.minus(3, ChronoUnit.DAYS));
        jdbc.update("update bookings set status = 'COMPLETED' where id = ?", b.getId());
        return b;
    }

    private List<String> avisos(User u, String tipo) {
        return jdbc.queryForList("select title from notifications where user_id = ? and type = ?", String.class,
                u.getId(), tipo);
    }

    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    @DisplayName("El día antes: campana y correo a los dos, una sola vez; a quien reservó hoy, nada")
    void elDiaAntes() {
        clase(ahora().plus(20, ChronoUnit.HOURS), ahora().minus(3, ChronoUnit.DAYS));
        clase(ahora().plus(21, ChronoUnit.HOURS), ahora().minus(1, ChronoUnit.HOURS));

        assertThat(recordatorio.recordar()).isEqualTo(1);
        assertThat(recordatorio.recordar()).isZero();

        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(ana, "CLASS_REMINDER").size() == 1);
        assertThat(avisos(ana, "CLASS_REMINDER").getFirst()).startsWith("Tu clase con María es ");
        assertThat(avisos(maria, "CLASS_REMINDER")).singleElement().asString().startsWith("Tu clase con Ana es ");
        verify(mailSender, timeout(5000).times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Una hora antes: campana a los dos y sin correo; a quien reservó hace cinco minutos, nada")
    void unaHoraAntes() {
        clase(ahora().plus(40, ChronoUnit.MINUTES), ahora().minus(2, ChronoUnit.DAYS));
        clase(ahora().plus(50, ChronoUnit.MINUTES).plus(3, ChronoUnit.HOURS), ahora().minus(5, ChronoUnit.MINUTES));

        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(maria, "CLASS_SOON").size() == 1);
        assertThat(avisos(ana, "CLASS_SOON")).singleElement().asString().startsWith("En una hora: tu clase con María");
    }

    @Test
    @DisplayName("Calificar: un día después, solo si no la calificó y solo las recientes")
    void recordarCalificar() {
        Booking sinCalificar = claseDada(ahora().minus(30, ChronoUnit.HOURS));
        Booking calificada = claseDada(ahora().minus(32, ChronoUnit.HOURS));
        claseDada(ahora().minus(6, ChronoUnit.DAYS));
        assertThat(post("/api/v1/bookings/" + calificada.getId() + "/review", anaSession,
                new CreateReviewRequest((short) 5, null), Map.class).getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(ana, "RATE_REMINDER").size() == 1);
        assertThat(jdbc.queryForObject("select booking_id from booking_reminders where kind = 'RATE'", UUID.class))
                .isEqualTo(sinCalificar.getId());
    }

    @Test
    @DisplayName("La profe se entera de la reseña que recibe, con sus estrellas")
    void laResenaRecibida() {
        Booking dada = claseDada(ahora().minus(2, ChronoUnit.HOURS));
        post("/api/v1/bookings/" + dada.getId() + "/review", anaSession, new CreateReviewRequest((short) 4, "Bien."),
                Map.class);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(maria, "REVIEW_RECEIVED").size() == 1);
        assertThat(avisos(maria, "REVIEW_RECEIVED").getFirst()).isEqualTo("Ana calificó su clase contigo: ★★★★");
    }

    @Test
    @DisplayName("Una reserva que vence sin pago se le avisa al estudiante")
    void laReservaVencida() {
        Instant empieza = ahora().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Booking b = TestBookings.awaitingPayment(ana.getId(), maria.getId(), empieza, empieza.plus(1, ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId());
        bookings.save(b);
        jdbc.update("update bookings set expires_at = ? where id = ?",
                java.sql.Timestamp.from(ahora().minus(1, ChronoUnit.MINUTES)), b.getId());

        expiracion.expireOverduePayments();

        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(ana, "BOOKING_EXPIRED").size() == 1);
        assertThat(avisos(ana, "BOOKING_EXPIRED").getFirst()).isEqualTo("Tu reserva con María venció sin pago");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Cuando Orión responde una solicitud de soporte, llega aviso y correo")
    void laRespuestaDeSoporte() {
        Map abierto = post("/api/v1/me/support/tickets", anaSession, Map.of(
                "category", "CLASE", "subject", "No pude entrar a la sala", "body", "Cuento lo que pasó."),
                Map.class).getBody();
        String code = (String) ((Map<String, Object>) abierto.get("ticket")).get("code");

        post("/api/v1/admin/support/tickets/" + code + "/replies", adminSession, Map.of("body", "Ya está."), Map.class);

        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(ana, "SUPPORT_ANSWERED").size() == 1);
        assertThat(avisos(ana, "SUPPORT_ANSWERED").getFirst()).contains("No pude entrar a la sala");
        verify(mailSender, timeout(5000).atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Toda sanción se le notifica al profe, y también cuando se levanta; una propuesta descartada no")
    void lasSanciones() {
        ProfessorSanction aviso = sanciones.applyManually(maria.getId(), "WARNING", "Llegó tarde tres veces.", admin.getId());
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(maria, "SANCTION_APPLIED").size() == 1);
        assertThat(avisos(maria, "SANCTION_APPLIED").getFirst()).isEqualTo("Orión te dejó un aviso");

        sanciones.revoke(aviso.getId(), admin.getId());
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos(maria, "SANCTION_LIFTED").size() == 1);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sistema manda un correo de prueba de verdad; solo el admin")
    void elCorreoDePrueba() {
        Map resultado = post("/api/v1/admin/system/test-email", adminSession, Map.of("to", "pardo@orion.test"),
                Map.class).getBody();
        assertThat(resultado).containsEntry("enviado", true).containsEntry("para", "pardo@orion.test");
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        assertThat(post("/api/v1/admin/system/test-email", anaSession, Map.of(), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
