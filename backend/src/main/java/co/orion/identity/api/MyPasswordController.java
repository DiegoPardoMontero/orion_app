package co.orion.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.PasswordService;
import co.orion.identity.domain.User;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/password")
public class MyPasswordController {

    private final PasswordService passwordService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public MyPasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /**
     * Cualquier rol puede cambiar su propia contraseña. Las demás sesiones de la cuenta se cierran
     * solas en su siguiente petición ({@code FreshPrincipalFilter}); esta se renueva aquí —nuevo
     * id y el principal con el hash nuevo— para que quien la cambió no salga con ellas.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal OrionUserDetails principal,
                       @Valid @RequestBody ChangePasswordRequest body,
                       HttpServletRequest request, HttpServletResponse response) {
        User actualizado = passwordService.change(
                principal.user().getId(), body.currentPassword(), body.newPassword());

        OrionUserDetails renovado = new OrionUserDetails(actualizado);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                renovado, null, renovado.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        request.changeSessionId();
        contextRepository.saveContext(contexto, request, response);
    }
}
