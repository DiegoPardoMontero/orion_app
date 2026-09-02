package co.orion.reputation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

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

import co.orion.scheduling.TestBookings;
import co.orion.TestcontainersConfiguration;
import co.orion.identity.api.PagedProfessors;
import co.orion.identity.api.ProfessorDetail;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Gate de exhibición (<3 reseñas → sin promedio) y moderación (reportar/ocultar retira del agregado). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, ProfessorRatingIT.FrozenClockConfiguration.class})
class ProfessorRatingIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final Instant PAST = FROZEN_NOW.minus(Duration.ofHours(3));

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private BookingRepository bookings;
    @Autowired private ReviewRepository reviews;
    @Autowired private ProfessorMetricsRepository metrics;
    @Autowired private ProfessorProfileRepository profiles;

    private User maria;
    private User ana;
    private User beatriz;
    private User carlos;
    private Session mariaSession;
    private Session adminSession;
    /** Cada reserva de María necesita un starts_at distinto: UNIQUE (professor_id, starts_at). */
    private int slot;

    @BeforeEach
    void seed() {
        metrics.deleteAll();
        reviews.deleteAll();
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        beatriz = createUser("bea@orion.test", "Beatriz Ruiz", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Díaz", UserRole.STUDENT);
        createUser("admin@orion.test", "Admin", UserRole.ADMIN);

        ProfessorProfile profile = new ProfessorProfile(maria);
        profile.describe("Inglés conversacional", "Diez años de experiencia.");
        profile.changeRate(50000L);
        profile.publish();
        profiles.save(profile);
        approveTeacher(maria.getId());

        mariaSession = login("maria@orion.test");
        adminSession = login("admin@orion.test");
    }

    private UUID review(User student, short rating) {
        Instant startsAt = PAST.minus(Duration.ofHours(slot++));
        Booking booking = bookings.save(TestBookings.confirmed(student.getId(), maria.getId(),
                startsAt, BookingModality.VIRTUAL, null, student.getId()));
        ResponseEntity<ReviewResponse> response = post(
                "/api/v1/bookings/" + booking.getId() + "/review", login(student.getEmail()),
                new CreateReviewRequest(rating, "Comentario de " + student.getFullName()),
                ReviewResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private ProfessorDetail detail() {
        return rest.getForEntity("/api/v1/professors/" + maria.getId(), ProfessorDetail.class).getBody();
    }

    private PagedReviews publicReviews() {
        return rest.getForEntity("/api/v1/professors/" + maria.getId() + "/reviews", PagedReviews.class)
                .getBody();
    }

    @Test
    void withFewerThanThreeVisibleReviewsTheAverageIsHidden() {
        // Sin reseñas: promedio nulo, conteo cero.
        assertThat(detail().ratingAvg()).isNull();
        assertThat(detail().ratingCount()).isZero();

        review(ana, (short) 5);
        review(beatriz, (short) 4);

        // Dos reseñas: conteo real 2, pero el promedio sigue oculto por el gate.
        ProfessorDetail twoReviews = detail();
        assertThat(twoReviews.ratingCount()).isEqualTo(2);
        assertThat(twoReviews.ratingAvg()).isNull();
    }

    @Test
    void withThreeOrMoreVisibleReviewsTheAverageIsShown() {
        review(ana, (short) 5);
        review(beatriz, (short) 4);
        review(carlos, (short) 3);

        ProfessorDetail detail = detail();
        assertThat(detail.ratingCount()).isEqualTo(3);
        assertThat(detail.ratingAvg()).isEqualTo(4.0); // (5+4+3)/3

        // El buscador expone el mismo agregado en la tarjeta.
        PagedProfessors search = rest.getForEntity("/api/v1/professors?sort=RATING", PagedProfessors.class)
                .getBody();
        assertThat(search.content()).hasSize(1);
        assertThat(search.content().get(0).ratingCount()).isEqualTo(3);
        assertThat(search.content().get(0).ratingAvg()).isEqualTo(4.0);

        assertThat(publicReviews().totalElements()).isEqualTo(3);
    }

    @Test
    void hidingAReportedReviewRemovesItFromTheAggregateAndThePublicList() {
        UUID anasReview = review(ana, (short) 5);
        review(beatriz, (short) 4);
        review(carlos, (short) 3);

        // El profesor reporta la reseña de Ana.
        ResponseEntity<Void> reported = post(
                "/api/v1/reviews/" + anasReview + "/report", mariaSession,
                new ReportReviewRequest("Comentario injusto"), Void.class);
        assertThat(reported.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Aparece en la cola de moderación del admin.
        ResponseEntity<ReportedReviewResponse[]> queue = get(
                "/api/v1/admin/reviews/reported", adminSession, ReportedReviewResponse[].class);
        assertThat(queue.getBody()).hasSize(1);
        assertThat(queue.getBody()[0].id()).isEqualTo(anasReview);

        // El admin la oculta.
        ResponseEntity<Void> hidden = post(
                "/api/v1/admin/reviews/" + anasReview + "/hide", adminSession,
                new HideReviewRequest("Viola las normas"), Void.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Deja de contar: quedan 2 visibles (promedio oculto de nuevo) y desaparece del listado público.
        ProfessorDetail detail = detail();
        assertThat(detail.ratingCount()).isEqualTo(2);
        assertThat(detail.ratingAvg()).isNull();
        assertThat(publicReviews().totalElements()).isEqualTo(2);
        // La cola de moderación ya no la muestra (dejó de estar visible).
        assertThat(get("/api/v1/admin/reviews/reported", adminSession, ReportedReviewResponse[].class)
                .getBody()).isEmpty();
    }

    @Test
    void aStudentCannotReportAReview() {
        UUID anasReview = review(ana, (short) 5);

        ResponseEntity<Void> response = post(
                "/api/v1/reviews/" + anasReview + "/report", login(ana.getEmail()),
                new ReportReviewRequest("intento"), Void.class);

        // /reviews/*/report exige rol PROFESSOR: un estudiante recibe 403.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aProfessorCannotReportAnotherProfessorsReview() {
        User juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        UUID anasReview = review(ana, (short) 5); // reseña sobre María

        ResponseEntity<Void> response = post(
                "/api/v1/reviews/" + anasReview + "/report", login(juan.getEmail()),
                new ReportReviewRequest("no es mía"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theReportedQueueRequiresAdmin() {
        ResponseEntity<Map> response = get(
                "/api/v1/admin/reviews/reported", mariaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
