package co.orion.messaging.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import co.orion.messaging.persistence.ConversationRepository;
import co.orion.messaging.persistence.MessageRepository;
import co.orion.messaging.persistence.NotificationRepository;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;

/** Un mensaje nuevo deja una notificación in-app a la contraparte; contarla y marcarla leída. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, NotificationIT.SyncConfiguration.class})
class NotificationIT extends ApiIntegrationSupport {

    @TestConfiguration
    static class SyncConfiguration {
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

    private User ana;
    private User maria;
    private ApiIntegrationSupport.Session anaSession;
    private ApiIntegrationSupport.Session mariaSession;

    @BeforeEach
    void seed() {
        notifications.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);

        ProfessorProfile published = new ProfessorProfile(maria);
        published.publish();
        profiles.save(published);
        approveTeacher(maria.getId());

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    private UUID openAndSend() {
        UUID conversationId = post("/api/v1/conversations", anaSession,
                new CreateConversationRequest(maria.getId()), ConversationSummaryResponse.class).getBody().id();
        post("/api/v1/conversations/" + conversationId + "/messages", anaSession,
                new SendMessageRequest("Hola Maria, ¿tienes cupo el jueves?"), MessageResponse.class);
        return conversationId;
    }

    @Test
    void sendingAMessageNotifiesTheCounterpart() {
        openAndSend();

        // La notificación es para María (la contraparte), no para quien la envió.
        NotificationResponse[] mariaFeed = get(
                "/api/v1/me/notifications", mariaSession, NotificationResponse[].class).getBody();
        assertThat(mariaFeed).hasSize(1);
        assertThat(mariaFeed[0].type()).isEqualTo("MESSAGE");
        assertThat(mariaFeed[0].title()).contains("Ana");
        assertThat(mariaFeed[0].read()).isFalse();

        // Ana, que la envió, no recibe notificación.
        NotificationResponse[] anaFeed = get(
                "/api/v1/me/notifications", anaSession, NotificationResponse[].class).getBody();
        assertThat(anaFeed).isEmpty();
    }

    @Test
    void unreadCountReflectsAndDropsAfterReading() {
        openAndSend();

        UnreadCountResponse before = get(
                "/api/v1/me/notifications/unread-count", mariaSession, UnreadCountResponse.class).getBody();
        assertThat(before.count()).isEqualTo(1);

        UUID notificationId = get("/api/v1/me/notifications", mariaSession,
                NotificationResponse[].class).getBody()[0].id();

        ResponseEntity<Void> read = post(
                "/api/v1/me/notifications/" + notificationId + "/read", mariaSession, null, Void.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UnreadCountResponse after = get(
                "/api/v1/me/notifications/unread-count", mariaSession, UnreadCountResponse.class).getBody();
        assertThat(after.count()).isZero();
    }

    @Test
    void readAllClearsEverything() {
        openAndSend();
        // Un segundo mensaje: dos notificaciones para María.
        UUID conversationId = conversations.findAll().getFirst().getId();
        post("/api/v1/conversations/" + conversationId + "/messages", anaSession,
                new SendMessageRequest("¿o el viernes?"), MessageResponse.class);

        assertThat(get("/api/v1/me/notifications/unread-count", mariaSession,
                UnreadCountResponse.class).getBody().count()).isEqualTo(2);

        post("/api/v1/me/notifications/read-all", mariaSession, null, Void.class);

        assertThat(get("/api/v1/me/notifications/unread-count", mariaSession,
                UnreadCountResponse.class).getBody().count()).isZero();
    }
}
