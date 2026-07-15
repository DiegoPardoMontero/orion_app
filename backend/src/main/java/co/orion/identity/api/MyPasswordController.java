package co.orion.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.PasswordService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/password")
public class MyPasswordController {

    private final PasswordService passwordService;

    public MyPasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /** Cualquier rol puede cambiar su propia contraseña. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal OrionUserDetails principal,
                       @Valid @RequestBody ChangePasswordRequest body) {
        passwordService.change(principal.user().getId(), body.currentPassword(), body.newPassword());
    }
}
