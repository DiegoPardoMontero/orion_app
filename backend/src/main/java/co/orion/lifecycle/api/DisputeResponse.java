package co.orion.lifecycle.api;

import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.lifecycle.domain.Dispute;
import co.orion.scheduling.domain.BusinessZone;

public record DisputeResponse(UUID id,
                              UUID bookingId,
                              ZonedDateTime classAt,
                              String studentName,
                              String professorName,
                              String reasonCode,
                              String description,
                              String status,
                              String resolutionNote,
                              ZonedDateTime resolvedAt,
                              ZonedDateTime createdAt,
                              long amountCop) {

    public static DisputeResponse of(Dispute dispute,
                                     java.time.Instant classAt,
                                     String studentName,
                                     String professorName,
                                     long amountCop) {
        return new DisputeResponse(
                dispute.getId(),
                dispute.getBookingId(),
                classAt != null ? classAt.atZone(BusinessZone.BOGOTA) : null,
                studentName,
                professorName,
                dispute.getReasonCode().name(),
                dispute.getDescription(),
                dispute.getStatus().name(),
                dispute.getResolutionNote(),
                dispute.getResolvedAt() != null
                        ? dispute.getResolvedAt().atZone(BusinessZone.BOGOTA) : null,
                dispute.getCreatedAt() != null
                        ? dispute.getCreatedAt().atZone(BusinessZone.BOGOTA) : null,
                amountCop);
    }
}
