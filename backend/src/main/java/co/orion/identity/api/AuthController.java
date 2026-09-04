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

import java.util.Map;

import org.springframework.web.bind.annotation.RequestParam;

import co.orion.identity.application.PasswordResetService;
import co.orion.identity.application.ProfessorInviteService;
import co.orion.identity.application.RegistrationService;
import co.orion.identity.domain.User;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final ProfessorInviteService professorInviteService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager,
                          RegistrationService registrationService,
                          PasswordResetService passwordResetService,
                          ProfessorInviteService professorInviteService) {
        this.authenticationManager = authenticationManager;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.professorInviteService = professorInviteService;
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
        registrationService.register(body.fullName(), body.email(), body.password(),
                body.whatsappPhone(), body.wantsToTeach());
        return authenticateAndOpenSession(body.email(), body.password(), request, response);
    }

    /**
     * Pide un enlace de recuperación. Responde 204 SIEMPRE, exista o no el correo: no revelamos qué
     * direcciones tienen cuenta. Quien tenga acceso al buzón recibe el enlace; los demás, nada.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        passwordResetService.request(body.email());
    }

    /** Restablece la contraseña con el token del enlace. Token inválido o vencido → 422. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordResetService.reset(body.token(), body.newPassword());
    }

    /** Datos mínimos de una invitación (valida el token) para pintar la pantalla /invitacion. */
    @GetMapping("/invite")
    public Map<String, String> inviteInfo(@RequestParam String token) {
        return Map.of("email", professorInviteService.invitedEmail(token));
    }

    /**
     * El profesor acepta la invitación: completa sus datos y su contraseña, la cuenta pasa a ACTIVE
     * y abrimos sesión (mismo camino que login) para que aterrice directo en su disponibilidad.
     */
    @PostMapping("/accept-invite")
    public UserResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest body,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        User professor = professorInviteService.accept(body.token(), body.fullName(), body.password(),
                body.whatsappPhone(), body.headline(), body.bio());
        return authenticateAndOpenSession(professor.getEmail(), body.password(), request, response);
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
