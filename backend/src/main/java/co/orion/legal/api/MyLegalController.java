package co.orion.legal.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.legal.application.LegalDocumentService;
import co.orion.shared.security.OrionUserDetails;

/**
 * Lo que la app le tiene que pedir aceptar a quien entra. Hoy, al profe que no ha aceptado la
 * versión vigente del acuerdo del profesor (la 2.0 trae el mandato de recaudo). Cualquier cuenta con
 * sesión lo puede preguntar: a quien no enseña le responde una lista vacía.
 */
@RestController
@RequestMapping("/api/v1/me/legal")
public class MyLegalController {

    private final LegalDocumentService legal;

    public MyLegalController(LegalDocumentService legal) {
        this.legal = legal;
    }

    @GetMapping("/pending")
    public PendingResponse pending(@AuthenticationPrincipal OrionUserDetails principal) {
        return new PendingResponse(legal.pendientesAlEntrar(principal.user().getId(), principal.rolEfectivo())
                .stream().map(Enum::name).toList());
    }

    public record PendingResponse(List<String> documents) {
    }
}
