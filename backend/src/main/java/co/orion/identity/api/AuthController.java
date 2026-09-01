package co.orion.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import co.orion.identity.application.RegistrationService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final RegistrationService registrationService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager,
                          RegistrationService registrationService) {
        this.authenticationManager = authenticationManager;
        this.registrationService = registrationService;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        return authenticateAndOpenSession(body.email(), body.password(), request, response);
    }

    /**
     * Alta pública de estudiantes. Crea la cuenta y abre sesión de una vez —reautenticando con las
     * mismas credenciales, el mismo camino que login— para que el estudiante entre sin un segundo
     * paso. Si el correo ya existe, RegistrationService responde 409 y nunca se llega a la sesión.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest body,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        registrationService.register(body.fullName(), body.email(), body.password(), body.whatsappPhone());
        return authenticateAndOpenSession(body.email(), body.password(), request, response);
    }

    private UserResponse authenticateAndOpenSession(String email, String password,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email.toLowerCase(), password));

        // En Spring Security 6+ el contexto ya no se guarda solo: hay que persistirlo
        // explícitamente en la sesión, o la siguiente petición llegaría como anónima.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return UserResponse.from((OrionUserDetails) auth.getPrincipal());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OrionUserDetails principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return UserResponse.from(principal);
    }
}
