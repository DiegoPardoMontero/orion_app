package co.orion.legal.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Lo que la app le tiene que pedir aceptar a quien entra: los Términos y la política vigentes, y al
 * profe el acuerdo del profesor. Cualquier cuenta con sesión lo puede preguntar; al admin le responde
 * una lista vacía.
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

    /** «Aceptar los nuevos acuerdos»: uno o varios a la vez, cada uno con su constancia. */
    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal OrionUserDetails principal, @Valid @RequestBody AcceptRequest body,
                       HttpServletRequest request) {
        List<LegalDocumentCode> codes = body.documents().stream().map(MyLegalController::parse).toList();
        legal.aceptarAlEntrar(principal.user().getId(), principal.rolEfectivo(), codes, request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static LegalDocumentCode parse(String code) {
        try {
            return LegalDocumentCode.valueOf(code == null ? "" : code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Ese documento no existe");
        }
    }

    public record PendingResponse(List<String> documents) {
    }

    public record AcceptRequest(@NotEmpty List<String> documents) {
    }
}
