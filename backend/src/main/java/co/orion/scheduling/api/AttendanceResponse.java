package co.orion.scheduling.api;

import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.scheduling.domain.AttendanceRecord;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;

public record AttendanceResponse(UUID id,
                                 UUID bookingId,
                                 boolean present,
                                 String notes,
                                 ZonedDateTime recordedAt,
                                 String bookingStatus) {

    public static AttendanceResponse of(AttendanceRecord record, Booking booking) {
        return new AttendanceResponse(
                record.getId(),
                record.getBookingId(),
                record.isPresent(),
                record.getNotes(),
                record.getRecordedAt().atZone(BusinessZone.BOGOTA),
                booking.getStatus().name());
    }
}
