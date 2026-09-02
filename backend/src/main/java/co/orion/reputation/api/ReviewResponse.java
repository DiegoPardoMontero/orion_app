package co.orion.reputation.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.reputation.domain.Review;

/** La reseña recién creada, tal como la ve su autor. */
public record ReviewResponse(
        UUID id,
        UUID bookingId,
        short rating,
        String comment,
        Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBookingId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
