package co.orion.identity.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.EmailVerificationService;
import co.orion.identity.application.RegistrationService;
import co.orion.identity.application.SocialLoginService;
import co.orion.identity.application.SocialLoginService.PerfilSocial;
import co.orion.identity.application.SocialProviders;
import co.orion.identity.domain.User;
import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.security.IntentosDeAcceso;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo del login social que no es la redirección en sí: qué proveedores hay, quién está a medio
 * registrarse y completar ese registro.
 *
 * <p><strong>Completar es el alta, con sus mismas casillas.</strong> Quien llega nuevo desde Google
 * no tiene cuenta todavía: la cuenta nace aquí, cuando declara que es mayor de edad y acepta los
 * términos y la política de datos, cada cosa en su casilla —como en el registro con correo, y por
 * el mismo motivo: juntarlas viciaría la autorización—. La constancia se guarda con IP y navegador.
 */
@RestController
@RequestMapping("/api/v1/auth/social")
public class SocialAuthController {

    private final SocialProviders proveedores;
    private final SocialLoginService servicio;
    private final SocialLogin login;
    private final RegistrationService registro;
    private final LegalDocumentService legal;
    private final EmailVerificationService verificacion;
    private final IntentosDeAcceso intentos;

    public SocialAuthController(SocialProviders proveedores, SocialLoginService servicio,
                                SocialLogin login, RegistrationService registro,
                                LegalDocumentService legal, EmailVerificationService verificacion,
                                IntentosDeAcceso intentos) {
        this.proveedores = proveedores;
        this.servicio = servicio;
        this.login = login;
        this.registro = registro;
        this.legal = legal;
        this.verificacion = verificacion;
        this.intentos = intentos;
    }

    /** Los botones que la pantalla puede mostrar: solo los que están configurados aquí. */
    @GetMapping("/providers")
    public Proveedores providers() {
        return new Proveedores(proveedores.configurados());
    }

    @GetMapping("/pending")
    public Pendiente pending(HttpServletRequest http) {
        PerfilSocial perfil = pendiente(http);
        return new Pendiente(perfil.nombre(), perfil.correo(), perfil.proveedor().registrationId());
    }

    @PostMapping("/complete")
    public UserResponse complete(@Valid @RequestBody CompletarRequest body,
                                 HttpServletRequest http, HttpServletResponse response) {
        intentos.antesDeRegistro(http);
        PerfilSocial perfil = pendiente(http);

        User creado = servicio.completar(perfil, body.fullName().trim(), registro);
        legal.record(creado.getId(), LegalDocumentCode.TERMS,
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        legal.record(creado.getId(), LegalDocumentCode.PRIVACY,
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        if (!creado.isEmailVerified()) {
            verificacion.send(creado.getId());
        }

        http.getSession().removeAttribute(SocialLogin.PENDIENTE);
        login.abrirSesion(creado, http, response);
        return UserResponse.from(new OrionUserDetails(creado));
    }

    private static PerfilSocial pendiente(HttpServletRequest http) {
        HttpSession sesion = http.getSession(false);
        Object perfil = sesion == null ? null : sesion.getAttribute(SocialLogin.PENDIENTE);
        if (!(perfil instanceof PerfilSocial p)) {
            throw new ResourceNotFoundException(
                    "Tu ingreso con el proveedor venció. Vuelve a intentarlo desde el inicio.");
        }
        return p;
    }

    public record Proveedores(List<String> providers) {
    }

    public record Pendiente(String name, String email, String provider) {
    }

    public record CompletarRequest(
            @NotBlank(message = "Dinos cómo te llamas.") @Size(max = 150) String fullName,
            @AssertTrue(message = "Orión está disponible solo para mayores de 18 años.") boolean adult,
            @AssertTrue(message = "Debes aceptar los Términos y condiciones para crear tu cuenta.")
            boolean acceptsTerms,
            @AssertTrue(message = "Necesitamos tu autorización para tratar tus datos personales.")
            boolean acceptsDataPolicy) {
    }
}
