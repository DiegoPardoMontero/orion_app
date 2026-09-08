package co.orion.support.api;

import java.time.Clock;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.shared.security.OrionUserDetails;
import co.orion.support.application.SupportService;
import jakarta.validation.Valid;

/** La bandeja del administrador. La protección /api/v1/admin/** la da SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

    private final SupportService support;
    private final Clock clock;

    public AdminSupportController(SupportService support, Clock clock) {
        this.support = support;
        this.clock = clock;
    }

    /** Todo lo que no está cerrado, ordenado por lo que vence antes. */
    @GetMapping("/tickets")
    public List<TicketSummary> inbox() {
        var now = clock.instant();
        return support.bandeja().stream().map(t -> TicketSummary.from(t, now)).toList();
    }

    @GetMapping("/tickets/{code}")
    public TicketThread one(@AuthenticationPrincipal OrionUserDetails principal,
                            @PathVariable String code) {
        return TicketThread.from(support.verComoAdmin(code), principal.user().getId(),
                clock.instant());
    }

    @PostMapping("/tickets/{code}/replies")
    public TicketThread reply(@AuthenticationPrincipal OrionUserDetails principal,
                              @PathVariable String code,
                              @Valid @RequestBody ReplyRequest body) {
        return TicketThread.from(
                support.responder(principal.user().getId(), code, body.body(), true),
                principal.user().getId(), clock.instant());
    }

    @PostMapping("/tickets/{code}/close")
    public TicketThread close(@AuthenticationPrincipal OrionUserDetails principal,
                              @PathVariable String code) {
        return TicketThread.from(support.cerrar(code), principal.user().getId(), clock.instant());
    }
}
