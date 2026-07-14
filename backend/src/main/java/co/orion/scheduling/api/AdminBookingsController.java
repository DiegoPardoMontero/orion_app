package co.orion.scheduling.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.AdminBookingService;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.shared.error.BusinessRuleViolationException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminBookingsController {

    private final AdminBookingService adminBookings;

    public AdminBookingsController(AdminBookingService adminBookings) {
        this.adminBookings = adminBookings;
    }

    @GetMapping("/bookings")
    public List<AdminBookingResponse> bookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID professorId,
            @RequestParam(required = false) String status) {
        return adminBookings.search(from, to, professorId, parseStatus(status)).stream()
                .map(AdminBookingResponse::from)
                .toList();
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        var metrics = adminBookings.metrics();
        return new MetricsResponse(metrics.bookingsLast7Days(), metrics.selfServicePctAllTime());
    }

    public record MetricsResponse(long bookingsLast7Days, double selfServicePctAllTime) {
    }

    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("status no es un estado válido de reserva");
        }
    }
}
