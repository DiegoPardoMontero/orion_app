package co.orion.scheduling.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.AttendanceService;
import co.orion.scheduling.application.BookingService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingsController {

    private final BookingService bookingService;
    private final AttendanceService attendanceService;

    public BookingsController(BookingService bookingService, AttendanceService attendanceService) {
        this.bookingService = bookingService;
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal OrionUserDetails principal,
                                  @Valid @RequestBody CreateBookingRequest body) {
        return BookingResponse.from(bookingService.create(
                principal.user(),
                body.professorId(),
                body.startsAt().toInstant(),
                body.modality(),
                body.locationNote(),
                body.studentId()));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody(required = false) CancelBookingRequest body) {
        String reason = body != null ? body.reason() : null;
        return BookingResponse.from(bookingService.cancel(principal.user(), id, reason));
    }

    @PostMapping("/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse recordAttendance(@AuthenticationPrincipal OrionUserDetails principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody RecordAttendanceRequest body) {
        AttendanceService.AttendanceResult result = attendanceService.record(
                principal.user(), id, body.present(), body.notes());
        return AttendanceResponse.of(result.record(), result.booking());
    }
}
