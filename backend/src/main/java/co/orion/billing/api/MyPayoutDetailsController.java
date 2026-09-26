package co.orion.billing.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.PayoutDetailsService;
import co.orion.billing.domain.BreBKeyType;
import co.orion.billing.domain.IdDocumentType;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Los datos de pago del propio profe, enmascarados. Nunca devuelve la llave ni el documento
 * completos: ni siquiera a su dueño, que ya los conoce, porque una sesión robada no debería bastar
 * para leerlos (brief de liquidaciones, regla 13).
 */
@RestController
@RequestMapping("/api/v1/me/payout-details")
public class MyPayoutDetailsController {

    private final PayoutDetailsService details;

    public MyPayoutDetailsController(PayoutDetailsService details) {
        this.details = details;
    }

    @GetMapping
    public MineResponse mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return new MineResponse(details.mine(principal.user().getId()).orElse(null));
    }

    @PutMapping
    public MineResponse save(@AuthenticationPrincipal OrionUserDetails principal,
                             @Valid @RequestBody SaveRequest body) {
        return new MineResponse(details.save(principal.user().getId(), body.keyType(), body.key(),
                body.documentType(), body.documentNumber(), body.holderName()));
    }

    /** {@code details} es null mientras el profe no los haya registrado. */
    public record MineResponse(PayoutDetailsService.Masked details) {
    }

    public record SaveRequest(@NotNull BreBKeyType keyType,
                              @NotNull @Size(max = 100) String key,
                              @NotNull IdDocumentType documentType,
                              @NotNull @Size(max = 30) String documentNumber,
                              @NotNull @Size(max = 150) String holderName) {
    }
}
