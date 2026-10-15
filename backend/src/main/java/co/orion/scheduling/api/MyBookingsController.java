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
import co.orion.identity.application.AdolescentProfileService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/bookings")
public class MyBookingsController {

    private final BookingQueryService bookingQueryService;
    private final AdolescentProfileService adolescents;

    public MyBookingsController(BookingQueryService bookingQueryService, AdolescentProfileService adolescents) {
        this.bookingQueryService = bookingQueryService;
        this.adolescents = adolescents;
    }

    @GetMapping
    public List<MyBookingResponse> myBookings(@AuthenticationPrincipal OrionUserDetails principal,
                                              @RequestParam(defaultValue = "upcoming") String scope) {
        var vistas = bookingQueryService.myBookings(principal.user(), parseScope(scope));
        // Para quién es cada clase de adolescente, resuelto una vez para toda la lista (Bloque 12).
        Map<UUID, String> adolescentes = adolescents.namesOf(vistas.stream()
                .map(v -> v.booking().getAdolescentProfileId()).filter(Objects::nonNull).toList());
        return vistas.stream()
                .map(view -> MyBookingResponse.of(view.booking(), view.counterpart(),
                        view.counterpartPhotoUrl(), view.counterpartHeadline(), view.now(),
                        view.cancellationWindow(), adolescentes.get(view.booking().getAdolescentProfileId())))
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
