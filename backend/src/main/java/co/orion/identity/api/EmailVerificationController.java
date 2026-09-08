package co.orion.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.EmailVerificationService;
import co.orion.shared.security.OrionUserDetails;

/** Reenvío del correo de verificación, para quien lo perdió o nunca le llegó. */
@RestController
@RequestMapping("/api/v1/me/account/email-verification")
public class EmailVerificationController {

    private final EmailVerificationService verification;

    public EmailVerificationController(EmailVerificationService verification) {
        this.verification = verification;
    }

    /**
     * Reenvía el enlace. Limitado a tres por hora dentro del servicio: sin ese freno, un botón que
     * manda correos es un altavoz apuntando al buzón de cualquiera cuyo correo se conozca.
     */
    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(@AuthenticationPrincipal OrionUserDetails principal) {
        verification.send(principal.user().getId());
    }
}
