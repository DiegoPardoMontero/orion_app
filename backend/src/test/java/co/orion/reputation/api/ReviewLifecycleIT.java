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
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Reglas de creación de reseña: participante, ventana temporal, unicidad y validación del cuerpo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, ReviewLifecycleIT.FrozenClockConfiguration.class})
class ReviewLifecycleIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    /** Empezó hace 3 horas: la clase ya ocurrió, es reseñable. */
    private static final Instant ALREADY_HAPPENED = FROZEN_NOW.minus(Duration.ofHours(3));
    /** Empieza mañana: aún no ocurre. */
    private static final Instant FUTURE = FROZEN_NOW.plus(Duration.ofDays(1));
    /** Empezó hace 40 días: fuera del plazo de 30. */
    private static final Instant LONG_AGO = FROZEN_NOW.minus(Duration.ofDays(40));

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

    private User ana;
    private User beatriz;
    private User maria;
    private Session anaSession;
    private Session beatrizSession;

    @BeforeEach
    void seed() {
        metrics.deleteAll();
        reviews.deleteAll();
        bookings.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        beatriz = createUser("bea@orion.test", "Beatriz Ruiz", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);

        anaSession = login("ana@orion.test");
        beatrizSession = login("bea@orion.test");
    }

    private Booking bookingOf(User student, Instant startsAt) {
        return bookings.save(TestBookings.confirmed(student.getId(), maria.getId(), startsAt,
                BookingModality.VIRTUAL, null, student.getId()));
    }

    private String reviewUrl(Booking booking) {
        return "/api/v1/bookings/" + booking.getId() + "/review";
    }

    @Test
    void aStudentReviewsAPastConfirmedClass() {
        Booking booking = bookingOf(ana, ALREADY_HAPPENED);

        ResponseEntity<ReviewResponse> response = post(
                reviewUrl(booking), anaSession, new CreateReviewRequest((short) 5, "Excelente clase"),
                ReviewResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().rating()).isEqualTo((short) 5);
        assertThat(response.getBody().comment()).isEqualTo("Excelente clase");
        assertThat(reviews.existsByBookingId(booking.getId())).isTrue();
        // El agregado se materializó: 1 reseña visible (aún bajo el umbral de 3 para mostrar promedio).
        assertThat(metrics.findById(maria.getId()).orElseThrow().getRatingCount()).isEqualTo(1);
    }

    @Test
    void reviewingTwiceIsAConflict() {
        Booking booking = bookingOf(ana, ALREADY_HAPPENED);
        post(reviewUrl(booking), anaSession, new CreateReviewRequest((short) 5, null), ReviewResponse.class);

        ResponseEntity<Map> second = post(
                reviewUrl(booking), anaSession, new CreateReviewRequest((short) 3, null), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reviewingAFutureClassIsUnprocessable() {
        Booking booking = bookingOf(ana, FUTURE);

        ResponseEntity<Map> response = post(
                reviewUrl(booking), anaSession, new CreateReviewRequest((short) 5, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(reviews.existsByBookingId(booking.getId())).isFalse();
    }

    @Test
    void reviewingAClassOlderThan30DaysIsUnprocessable() {
        Booking booking = bookingOf(ana, LONG_AGO);

        ResponseEntity<Map> response = post(
                reviewUrl(booking), anaSession, new CreateReviewRequest((short) 5, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void aNonParticipantCannotReview() {
        // La reserva es de Ana; Beatriz intenta reseñarla.
        Booking anasClass = bookingOf(ana, ALREADY_HAPPENED);

        ResponseEntity<Map> response = post(
                reviewUrl(anasClass), beatrizSession, new CreateReviewRequest((short) 1, "No fui yo"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reviews.existsByBookingId(anasClass.getId())).isFalse();
    }

    @Test
    void aCommentLongerThan1000IsABadRequest() {
        Booking booking = bookingOf(ana, ALREADY_HAPPENED);
        String tooLong = "x".repeat(1001);

        ResponseEntity<Map> response = post(
                reviewUrl(booking), anaSession, new CreateReviewRequest((short) 5, tooLong), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reviews.existsByBookingId(booking.getId())).isFalse();
    }

    @Test
    void reviewingAnUnknownBookingIsNotFound() {
        ResponseEntity<Map> response = post(
                "/api/v1/bookings/" + UUID.randomUUID() + "/review", anaSession,
                new CreateReviewRequest((short) 5, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
