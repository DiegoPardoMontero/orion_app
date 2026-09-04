package co.orion.scheduling.api;

import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.scheduling.domain.RescheduleRequest;
import co.orion.shared.time.BusinessZone;

/**
 * Una propuesta de cambio de horario. {@code mine} le dice al frontend si quien mira es quien la
 * propuso —y por tanto espera respuesta— o quien tiene que responderla.
 */
public record RescheduleRequestResponse(UUID id,
                                        UUID bookingId,
                                        ZonedDateTime proposedStartsAt,
                                        ZonedDateTime proposedEndsAt,
                                        String reason,
                                        String status,
                                        boolean mine,
                                        ZonedDateTime createdAt) {

    public static RescheduleRequestResponse of(RescheduleRequest request, UUID viewerId) {
        return new RescheduleRequestResponse(
                request.getId(),
                request.getBookingId(),
                request.getProposedStartsAt().atZone(BusinessZone.BOGOTA),
                request.getProposedEndsAt().atZone(BusinessZone.BOGOTA),
                request.getReason(),
                request.getStatus().name(),
                request.getRequestedBy().equals(viewerId),
                request.getCreatedAt() != null
                        ? request.getCreatedAt().atZone(BusinessZone.BOGOTA) : null);
    }
}
