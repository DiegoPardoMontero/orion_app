package co.orion.reputation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.reputation.api.PagedReviews;
import co.orion.reputation.api.PublicReviewResponse;
import co.orion.reputation.api.ReportedReviewResponse;
import co.orion.reputation.domain.ProfessorMetrics;
import co.orion.reputation.domain.Review;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

/**
 * Las reseñas: crearlas (estudiante), reportarlas (profesor) y ocultarlas (admin), más el listado
 * público y la cola de moderación. Cada creación u ocultamiento recalcula el agregado del profesor
 * SOLO sobre las visibles.
 */
@Service
public class ReviewService {

    /**
     * Plazo para reseñar tras la clase. La reserva es "reseñable" si ocurrió (no cancelada) y su
     * inicio cae en esta ventana hacia atrás desde ahora. Como no existe un estado COMPLETED
     * garantizado (la asistencia es opcional), aceptamos tanto CONFIRMED con inicio ya pasado como
     * COMPLETED: ambos significan que la clase se dio.
     */
    static final Duration REVIEW_WINDOW = Duration.ofDays(30);

    private final ReviewRepository reviews;
    private final ProfessorMetricsRepository metrics;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews,
                         ProfessorMetricsRepository metrics,
                         BookingRepository bookings,
                         UserRepository users,
                         Clock clock) {
        this.reviews = reviews;
        this.metrics = metrics;
        this.bookings = bookings;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public Review create(User student, UUID bookingId, short rating, String comment) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        // No-participante: 403. La reseña la deja SOLO el estudiante de la reserva.
        if (!booking.getStudentId().equals(student.getId())) {
            throw new ForbiddenException("No puedes reseñar una reserva que no es tuya.");
        }

        Instant now = clock.instant();
        assertReviewable(booking, now);

        // Chequeo amable (409) antes de tocar la BD; el árbitro final es el UNIQUE de booking_id.
        if (reviews.existsByBookingId(bookingId)) {
            throw new ConflictException("Ya reseñaste esta clase.");
        }

        Review review = new Review(bookingId, student.getId(), booking.getProfessorId(), rating, comment);
        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            // Carrera perdida: otra reseña de la misma reserva entró entre el chequeo y el INSERT.
            throw new ConflictException("Ya reseñaste esta clase.");
        }

        recompute(booking.getProfessorId(), now);
        return review;
    }

    @Transactional
    public Review report(User professor, UUID reviewId, String reason) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada"));
        // Solo el profesor reseñado puede reportarla. 403 a cualquier otro.
        if (!review.getProfessorId().equals(professor.getId())) {
            throw new ForbiddenException("No puedes reportar una reseña que no es sobre ti.");
        }
        review.report(reason, clock.instant());
        reviews.save(review);
        return review;
    }

    @Transactional
    public Review hide(UUID reviewId, UUID adminId, String reason) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada"));
        review.hide(adminId, reason);
        reviews.save(review);
        // Al ocultarla deja de contar: recalculamos el agregado del profesor.
        recompute(review.getProfessorId(), clock.instant());
        return review;
    }

    @Transactional(readOnly = true)
    public PagedReviews publicReviews(UUID professorId, int page, int size) {
        Page<Review> found = reviews.findByProfessorIdAndVisibleTrueOrderByCreatedAtDesc(
                professorId, PageRequest.of(Math.max(page, 0), clampSize(size)));

        Map<UUID, User> studentsById = usersByIds(
                found.getContent().stream().map(Review::getStudentId));

        List<PublicReviewResponse> content = found.getContent().stream()
                .map(r -> new PublicReviewResponse(
                        r.getId(),
                        nameOf(studentsById, r.getStudentId()),
                        r.getRating(),
                        r.getComment(),
                        r.getCreatedAt()))
                .toList();

        return new PagedReviews(content, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ReportedReviewResponse> reportedReviews() {
        List<Review> reported = reviews.findByReportedAtNotNullAndVisibleTrueOrderByReportedAtDesc();
        Map<UUID, User> byId = usersByIds(reported.stream()
                .flatMap(r -> Stream.of(r.getStudentId(), r.getProfessorId())));

        return reported.stream()
                .map(r -> new ReportedReviewResponse(
                        r.getId(),
                        r.getProfessorId(),
                        nameOf(byId, r.getProfessorId()),
                        r.getStudentId(),
                        nameOf(byId, r.getStudentId()),
                        r.getRating(),
                        r.getComment(),
                        r.getReportedAt(),
                        r.getReportedReason(),
                        r.getCreatedAt()))
                .toList();
    }

    // --- helpers ---

    private void assertReviewable(Booking booking, Instant now) {
        BookingStatus status = booking.getStatus();
        boolean occurred = status == BookingStatus.CONFIRMED || status == BookingStatus.COMPLETED;
        if (!occurred) {
            throw new UnprocessableException("Solo puedes reseñar una clase que se dio.");
        }
        if (booking.getStartsAt().isAfter(now)) {
            throw new UnprocessableException("La clase aún no ha ocurrido.");
        }
        if (now.isAfter(booking.getStartsAt().plus(REVIEW_WINDOW))) {
            throw new UnprocessableException("El plazo de 30 días para reseñar esta clase ya venció.");
        }
    }

    private void recompute(UUID professorId, Instant now) {
        ReviewRepository.RatingAggregate agg = reviews.aggregateVisible(professorId);
        int count = (int) agg.getTotal();
        BigDecimal avg = agg.getAverage() == null
                ? null
                : BigDecimal.valueOf(agg.getAverage()).setScale(2, RoundingMode.HALF_UP);

        ProfessorMetrics row = metrics.findById(professorId)
                .orElseGet(() -> new ProfessorMetrics(professorId));
        row.recompute(avg, count, now);
        metrics.save(row);
    }

    private Map<UUID, User> usersByIds(Stream<UUID> ids) {
        Set<UUID> distinct = ids.collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String nameOf(Map<UUID, User> byId, UUID id) {
        User user = byId.get(id);
        return user != null ? user.getFullName() : null;
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 50);
    }
}
