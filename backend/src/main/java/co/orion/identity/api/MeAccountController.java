package co.orion.identity.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.AccountService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** La cuenta del usuario autenticado. Cualquier rol edita su propio nombre y WhatsApp. */
@RestController
@RequestMapping("/api/v1/me/account")
public class MeAccountController {

    private final AccountService accountService;

    public MeAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public MeAccountResponse me(@AuthenticationPrincipal OrionUserDetails principal) {
        return MeAccountResponse.from(accountService.get(principal.user().getId()));
    }

    @PutMapping
    public MeAccountResponse update(@AuthenticationPrincipal OrionUserDetails principal,
                                    @Valid @RequestBody UpdateAccountRequest body) {
        return MeAccountResponse.from(
                accountService.update(principal.user().getId(), body.fullName(), body.whatsappPhone()));
    }
}
