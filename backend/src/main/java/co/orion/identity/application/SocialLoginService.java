package co.orion.identity.application;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.SocialIdentity;
import co.orion.identity.domain.SocialProvider;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.SocialIdentityRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ConflictException;

/**
 * Qué pasa cuando alguien vuelve de Google, Apple o Facebook.
 *
 * <p>Tres salidas, y la regla que las separa es la seguridad de la cuenta:
 * <ul>
 *   <li><strong>Entra</strong> si esa identidad ya está vinculada, o si ya hay una cuenta con ese
 *       correo <em>y el proveedor lo verificó</em>: entonces se vincula y entra. Sin verificación
 *       no se vincula nunca — cualquiera podría crear una cuenta de proveedor con el correo de otra
 *       persona y quedarse con su cuenta de Orión.</li>
 *   <li><strong>Completa</strong> si es alguien nuevo: la cuenta no se crea sin las tres casillas
 *       del alta (mayoría de edad, términos, autorización de datos), igual que en el registro con
 *       correo. Se le piden una vez, en una pantalla corta, y ahí nace la cuenta.</li>
 *   <li><strong>Se rechaza</strong> con un motivo que la pantalla sabe decir: cuenta inactiva, un
 *       proveedor que no comparte correo, o un correo que ya existe sin verificar.</li>
 * </ul>
 */
@Service
public class SocialLoginService {

    private final SocialIdentityRepository identidades;
    private final UserRepository users;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SocialLoginService(SocialIdentityRepository identidades, UserRepository users,
                              PasswordEncoder passwordEncoder, Clock clock) {
        this.identidades = identidades;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Lo que se sabe de la persona al volver del proveedor. */
    public record PerfilSocial(SocialProvider proveedor, String sujeto, String correo,
                               boolean correoVerificado, String nombre) implements Serializable {
    }

    public sealed interface Resultado permits Entra, Completa, Rechazado {
    }

    public record Entra(User user) implements Resultado {
    }

    /** Alguien nuevo: se guarda en la sesión hasta que complete las casillas. */
    public record Completa(PerfilSocial perfil) implements Resultado {
    }

    /** @param motivo el código que la pantalla de login traduce a una frase */
    public record Rechazado(String motivo) implements Resultado {
    }

    @Transactional
    public Resultado resolver(PerfilSocial perfil) {
        Optional<SocialIdentity> vinculada =
                identidades.findByProviderAndSubject(perfil.proveedor(), perfil.sujeto());
        if (vinculada.isPresent()) {
            return users.findById(vinculada.get().getUserId())
                    .<Resultado>map(u -> u.isActive() ? new Entra(u) : new Rechazado("cuenta-inactiva"))
                    .orElse(new Rechazado("error"));
        }

        if (perfil.correo() == null || perfil.correo().isBlank()) {
            return new Rechazado("sin-correo");
        }

        Optional<User> existente = users.findByEmailIgnoreCase(perfil.correo());
        if (existente.isPresent()) {
            if (!perfil.correoVerificado()) {
                return new Rechazado("correo-sin-verificar");
            }
            User user = existente.get();
            if (!user.isActive()) {
                return new Rechazado("cuenta-inactiva");
            }
            if (!user.isEmailVerified()) {
                tomarPosesion(user);
            }
            vincular(user, perfil);
            return new Entra(user);
        }

        return new Completa(perfil);
    }

    /**
     * Crea la cuenta de alguien que llegó por un proveedor y completó las casillas. La cuenta nace
     * sin contraseña utilizable (entra con su proveedor, y puede ponerse una con «recuperar
     * contraseña») y con el correo verificado si el proveedor lo garantizó.
     */
    @Transactional
    public User completar(PerfilSocial perfil, String nombre, boolean quiereEnsenar, RegistrationService registro) {
        if (identidades.findByProviderAndSubject(perfil.proveedor(), perfil.sujeto()).isPresent()) {
            throw new ConflictException("Esta cuenta ya está vinculada. Entra de nuevo.");
        }
        User creado = registro.registerFromProvider(nombre, perfil.correo(), perfil.correoVerificado(), quiereEnsenar);
        vincular(creado, perfil);
        return creado;
    }

    /**
     * Cierra la «cuenta preparada»: alguien se registra con el correo de otra persona y una
     * contraseña que él conoce, sin poder confirmarlo; meses después la dueña real entra con
     * Google, se vincula a esa cuenta, y el primero sigue teniendo llave. El proveedor acaba de
     * probar quién es la dueña del buzón, así que la cuenta pasa a ella: correo verificado y
     * contraseña anulada. Si la contraseña era suya, la recupera con «olvidé mi contraseña»,
     * porque ahora el buzón es lo que manda.
     */
    private void tomarPosesion(User user) {
        byte[] secreto = new byte[32];
        RANDOM.nextBytes(secreto);
        user.changePasswordHash(passwordEncoder.encode(HexFormat.of().formatHex(secreto)));
        user.markEmailVerified(clock.instant());
        users.save(user);
    }

    private void vincular(User user, PerfilSocial perfil) {
        identidades.save(new SocialIdentity(user.getId(), perfil.proveedor(), perfil.sujeto(),
                perfil.correo(), clock.instant()));
    }
}
