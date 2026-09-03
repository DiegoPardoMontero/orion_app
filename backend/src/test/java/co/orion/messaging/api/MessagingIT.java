package co.orion.messaging.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.messaging.domain.FlaggedReason;
import co.orion.messaging.domain.Message;
import co.orion.messaging.persistence.ConversationRepository;
import co.orion.messaging.persistence.MessageRepository;
import co.orion.messaging.persistence.NotificationRepository;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;
import static org.mockito.Mockito.when;

/**
 * El flujo de mensajería de extremo a extremo: abrir hilo (con el gate del profesor aprobado),
 * escribir con enmascarado, leer marcando leídos, responder, y la privacidad frente a terceros.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, MessagingIT.SyncConfiguration.class})
class MessagingIT extends ApiIntegrationSupport {

    @TestConfiguration
    static class SyncConfiguration {
        // Listener de mensajes @Async en producción; síncrono aquí para que la notificación y el
        // correo AFTER_COMMIT ya estén cuando el test lee la respuesta.
        @Bean
        Executor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private BookingRepository bookings;

    private User ana;      // estudiante
    private User maria;    // profesora aprobada y publicada
    private User pedro;    // profesor NO aprobado
    private User sofia;    // tercera en discordia (otra estudiante)

    private ApiIntegrationSupport.Session anaSession;
    private ApiIntegrationSupport.Session mariaSession;

    @BeforeEach
    void seed() {
        notifications.deleteAll();
        bookings.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        pedro = createUser("pedro@orion.test", "Pedro No Aprobado", UserRole.PROFESSOR);
        sofia = createUser("sofia@orion.test", "Sofía Tercera", UserRole.STUDENT);

        ProfessorProfile published = new ProfessorProfile(maria);
        published.publish();
        profiles.save(published);
        approveTeacher(maria.getId());

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    private ConversationSummaryResponse openWithMaria() {
        return post("/api/v1/conversations", anaSession,
                new CreateConversationRequest(maria.getId()), ConversationSummaryResponse.class).getBody();
    }

    @Test
    void aStudentOpensAConversationWithAnApprovedProfessor() {
        ResponseEntity<ConversationSummaryResponse> response = post("/api/v1/conversations", anaSession,
                new CreateConversationRequest(maria.getId()), ConversationSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().counterpart().id()).isEqualTo(maria.getId());
        assertThat(conversations.findAll()).hasSize(1);
    }

    @Test
    void openingTwiceReusesTheSameConversation() {
        UUID first = openWithMaria().id();
        UUID second = openWithMaria().id();

        assertThat(first).isEqualTo(second);
        assertThat(conversations.findAll()).hasSize(1);
    }

    /**
     * El otro lado del hilo. El profesor no elige a quién enseñar del mismo modo que el estudiante
     * elige profesor, así que su puerta es más estrecha: solo quien ya reservó con él.
     */
    @Test
    void aProfessorOpensAConversationWithAStudentWhoBookedWithThem() {
        bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(),
                java.time.Instant.parse("2026-07-15T14:00:00Z"), BookingModality.VIRTUAL, null, ana.getId()));

        ResponseEntity<ConversationSummaryResponse> response = post("/api/v1/conversations", mariaSession,
                new CreateConversationRequest(ana.getId()), ConversationSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().counterpart().id()).isEqualTo(ana.getId());
    }

    /** Sin clase de por medio, la bandeja del estudiante quedaría abierta a mensajes no pedidos. */
    @Test
    void aProfessorCannotOpenWithAStudentWhoNeverBookedWithThem() {
        ResponseEntity<String> response = post("/api/v1/conversations", mariaSession,
                new CreateConversationRequest(sofia.getId()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(conversations.findAll()).isEmpty();
    }

    /** Una clase cancelada también cuenta: hablarlo es justo lo que hay que poder hacer. */
    @Test
    void aCancelledClassStillLetsTheProfessorWrite() {
        var booking = TestBookings.confirmed(ana.getId(), maria.getId(),
                java.time.Instant.parse("2026-07-15T14:00:00Z"), BookingModality.VIRTUAL, null, ana.getId());
        booking.cancel(co.orion.scheduling.domain.BookingStatus.CANCELLED_BY_PROFESSOR, maria.getId(),
                java.time.Instant.parse("2026-07-14T14:00:00Z"), "Imprevisto");
        bookings.save(booking);

        ResponseEntity<ConversationSummaryResponse> response = post("/api/v1/conversations", mariaSession,
                new CreateConversationRequest(ana.getId()), ConversationSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Los dos lados llegan al MISMO hilo: no hay una conversación por dirección. */
    @Test
    void bothSidesLandOnTheSameThread() {
        bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(),
                java.time.Instant.parse("2026-07-15T14:00:00Z"), BookingModality.VIRTUAL, null, ana.getId()));

        UUID abiertaPorLaEstudiante = openWithMaria().id();
        UUID abiertaPorLaProfesora = post("/api/v1/conversations", mariaSession,
                new CreateConversationRequest(ana.getId()), ConversationSummaryResponse.class).getBody().id();

        assertThat(abiertaPorLaProfesora).isEqualTo(abiertaPorLaEstudiante);
        assertThat(conversations.findAll()).hasSize(1);
    }

    @Test
    void aStudentCannotOpenWithANonApprovedProfessor() {
        ResponseEntity<String> response = post("/api/v1/conversations", anaSession,
                new CreateConversationRequest(pedro.getId()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(conversations.findAll()).isEmpty();
    }

    @Test
    void aThirdPartyCannotReadTheConversation() {
        UUID conversationId = openWithMaria().id();
        var sofiaSession = login("sofia@orion.test");

        ResponseEntity<String> response = get(
                "/api/v1/conversations/" + conversationId + "/messages", sofiaSession, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aMessageWithAPhoneIsMaskedForThePartiesAndKeptOriginalForModeration() {
        UUID conversationId = openWithMaria().id();

        ResponseEntity<MessageResponse> response = post(
                "/api/v1/conversations/" + conversationId + "/messages", anaSession,
                new SendMessageRequest("Hola Maria, mi numero es 3001112233 por si acaso"),
                MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Lo que ven las partes va enmascarado.
        assertThat(response.getBody().body()).doesNotContain("3001112233");
        assertThat(response.getBody().flaggedReason()).isEqualTo("CONTACT_INFO");

        // El original se conserva en la base para moderación.
        Message stored = messages.findAll().getFirst();
        assertThat(stored.getBodyOriginal()).contains("3001112233");
        assertThat(stored.getFlaggedReason()).isEqualTo(FlaggedReason.CONTACT_INFO);
    }

    @Test
    void aMessageWithAnEmailIsMasked() {
        UUID conversationId = openWithMaria().id();

        ResponseEntity<MessageResponse> response = post(
                "/api/v1/conversations/" + conversationId + "/messages", anaSession,
                new SendMessageRequest("mi correo es ana@gmail.com"),
                MessageResponse.class);

        assertThat(response.getBody().body()).doesNotContain("ana@gmail.com");
        assertThat(response.getBody().flaggedReason()).isEqualTo("CONTACT_INFO");
    }

    @Test
    void theProfessorRepliesAndTheStudentReadsMarkingItRead() {
        UUID conversationId = openWithMaria().id();

        // Ana escribe; María responde.
        post("/api/v1/conversations/" + conversationId + "/messages", anaSession,
                new SendMessageRequest("Hola Maria"), MessageResponse.class);
        post("/api/v1/conversations/" + conversationId + "/messages", mariaSession,
                new SendMessageRequest("Hola Ana, con gusto"), MessageResponse.class);

        // Antes de leer, Ana tiene 1 sin leer (la respuesta de María).
        List<ConversationSummaryResponse> inboxBefore = List.of(
                get("/api/v1/conversations", anaSession, ConversationSummaryResponse[].class).getBody());
        assertThat(inboxBefore.getFirst().unreadCount()).isEqualTo(1);

        // Ana abre el hilo: se marcan leídos los ajenos.
        MessageResponse[] thread = get(
                "/api/v1/conversations/" + conversationId + "/messages", anaSession,
                MessageResponse[].class).getBody();
        assertThat(thread).hasSize(2);
        assertThat(thread[1].body()).contains("con gusto");
        assertThat(thread[1].mine()).isFalse();

        List<ConversationSummaryResponse> inboxAfter = List.of(
                get("/api/v1/conversations", anaSession, ConversationSummaryResponse[].class).getBody());
        assertThat(inboxAfter.getFirst().unreadCount()).isZero();
    }

    @Test
    void flaggedMessagesShowUpInTheAdminModerationQueue() {
        UUID conversationId = openWithMaria().id();
        post("/api/v1/conversations/" + conversationId + "/messages", anaSession,
                new SendMessageRequest("llámame al 3001112233"), MessageResponse.class);

        User admin = createUser("admin@orion.test", "Admin Orión", UserRole.ADMIN);
        var adminSession = login("admin@orion.test");

        FlaggedMessageResponse[] flagged = get(
                "/api/v1/admin/messages/flagged", adminSession, FlaggedMessageResponse[].class).getBody();

        assertThat(flagged).hasSize(1);
        assertThat(flagged[0].flaggedReason()).isEqualTo("CONTACT_INFO");
        assertThat(flagged[0].bodyOriginal()).contains("3001112233");
        assertThat(admin.getId()).isNotNull();
    }
}
