package co.orion.scheduling.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.BookingQueryService;
import co.orion.scheduling.application.BookingQueryService.Scope;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;

@RestController
@RequestMapping("/api/v1/me/bookings")
public class MyBookingsController {

    private final BookingQueryService bookingQueryService;

    public MyBookingsController(BookingQueryService bookingQueryService) {
        this.bookingQueryService = bookingQueryService;
    }

    @GetMapping
    public List<MyBookingResponse> myBookings(@AuthenticationPrincipal OrionUserDetails principal,
                                              @RequestParam(defaultValue = "upcoming") String scope) {
        return bookingQueryService.myBookings(principal.user(), parseScope(scope)).stream()
                .map(view -> MyBookingResponse.of(view.booking(), view.counterpart(),
                        view.counterpartPhotoUrl(), view.counterpartHeadline(), view.now()))
                .toList();
    }

    private Scope parseScope(String scope) {
        try {
            return Scope.valueOf(scope.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("scope debe ser upcoming o past");
        }
    }
}
