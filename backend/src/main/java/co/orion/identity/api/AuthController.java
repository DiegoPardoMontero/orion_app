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
import co.orion.identity.application.EmailVerificationService;
import co.orion.identity.application.RegistrationService;
import co.orion.identity.domain.User;
import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.shared.security.IntentosDeAcceso;
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
    private final LegalDocumentService legal;
    private final EmailVerificationService emailVerification;
    private final IntentosDeAcceso intentos;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager,
                          RegistrationService registrationService,
                          PasswordResetService passwordResetService,
                          ProfessorInviteService professorInviteService,
                          LegalDocumentService legal,
                          EmailVerificationService emailVerification,
                          IntentosDeAcceso intentos) {
        this.authenticationManager = authenticationManager;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.professorInviteService = professorInviteService;
        this.legal = legal;
        this.emailVerification = emailVerification;
        this.intentos = intentos;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        intentos.antesDeLogin(request, body.email());
        UserResponse me = authenticateAndOpenSession(body.email(), body.password(), request, response);
        // Solo si acertó: quien entra bien no debe arrastrar los fallos de antes. Va después de
        // authenticate(...) a propósito — si las credenciales fallan, la excepción sale antes y el
        // intento se queda contado.
        intentos.loginCorrecto(request, body.email());
        return me;
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
        intentos.antesDeRegistro(request);
        User creado = registrationService.register(body.fullName(), body.email(), body.password(),
                body.whatsappPhone(), body.wantsToTeach(), body.adult());

        // La constancia, con IP y user-agent, en la misma petición en que se dio. El art. 9 de la
        // Ley 1581 de 2012 exige poder PROBAR la autorización: una casilla marcada que no deja
        // rastro no es una autorización, es una afirmación nuestra.
        legal.record(creado.getId(), LegalDocumentCode.TERMS,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        legal.record(creado.getId(), LegalDocumentCode.PRIVACY,
                request.getRemoteAddr(), request.getHeader("User-Agent"));

        // El correo de confirmación sale ya. Su fallo no deshace el alta: la cuenta existe y hay
        // un botón de reenviar; perder la cuenta por un SMTP caído sería mucho peor.
        emailVerification.send(creado.getId());

        return authenticateAndOpenSession(body.email(), body.password(), request, response);
    }

    /**
     * Pide un enlace de recuperación. Responde 204 SIEMPRE, exista o no el correo: no revelamos qué
     * direcciones tienen cuenta. Quien tenga acceso al buzón recibe el enlace; los demás, nada.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        intentos.antesDeRecuperar(body.email());
        passwordResetService.request(body.email());
    }

    /**
     * Confirma una dirección de correo con el token del enlace. Público: quien llega desde su
     * buzón puede no tener sesión abierta —o tenerla en otro navegador—, y exigirle iniciar sesión
     * para confirmar un correo es pedirle que resuelva el problema antes de resolverlo.
     */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        emailVerification.verify(body.token());
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
