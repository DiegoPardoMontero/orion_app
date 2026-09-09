package co.orion.lifecycle.api;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.domain.RefundRequest;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.application.RetractionService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** La cola de devoluciones. La protección /api/v1/admin/** la da SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundsController {

    private final RetractionService retraction;
    private final UserRepository users;
    private final Clock clock;

    public AdminRefundsController(RetractionService retraction, UserRepository users, Clock clock) {
        this.retraction = retraction;
        this.users = users;
        this.clock = clock;
    }

    /** Lo que se debe devolver, con lo que vence antes primero. */
    @GetMapping
    public List<RefundResponse> pending() {
        List<RefundRequest> pendientes = retraction.pendientes();
        Map<UUID, String> nombres = users
                .findAllById(pendientes.stream().map(RefundRequest::getStudentId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));

        var now = clock.instant();
        return pendientes.stream()
                .map(r -> RefundResponse.from(r, nombres.getOrDefault(r.getStudentId(), "—"), now))
                .toList();
    }

    @PostMapping("/{refundId}/confirm")
    public RefundResponse confirm(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID refundId,
                                  @Valid @RequestBody ConfirmRefundRequest body) {
        RefundRequest solicitud = retraction.confirmarDevolucion(
                refundId, body.reference(), body.note(), principal.user().getId());
        String nombre = users.findById(solicitud.getStudentId())
                .map(User::getFullName).orElse("—");
        return RefundResponse.from(solicitud, nombre, clock.instant());
    }
}
