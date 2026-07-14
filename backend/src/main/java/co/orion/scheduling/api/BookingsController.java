package co.orion.scheduling.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.BookingService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingsController {

    private final BookingService bookingService;

    public BookingsController(BookingService bookingService) {
        this.bookingService = bookingService;
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
}
