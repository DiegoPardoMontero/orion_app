package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import co.orion.identity.application.SocialLoginService.PerfilSocial;
import co.orion.identity.domain.SocialProvider;

/** Lo que Microsoft dice de la persona, traducido a lo mismo que los demás. */
class PerfilSocialTest {

    private static DefaultOidcUser deMicrosoft(Map<String, Object> reclamos) {
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(300), reclamos);
        return new DefaultOidcUser(List.of(), token);
    }

    @Test
    @DisplayName("Microsoft: el correo nunca cuenta como verificado, aunque venga")
    void correoSinVerificar() {
        PerfilSocial p = SocialLogin.perfil("microsoft",
                deMicrosoft(Map.of("sub", "ms-1", "email", "ana@outlook.com", "name", "Ana Ruiz")), null);

        assertThat(p).isEqualTo(new PerfilSocial(SocialProvider.MICROSOFT, "ms-1", "ana@outlook.com", false, "Ana Ruiz"));
    }

    @Test
    @DisplayName("Microsoft sin «email»: el nombre de usuario si tiene forma de correo; si no, ninguno")
    void sinEmail() {
        assertThat(SocialLogin.perfil("microsoft",
                deMicrosoft(Map.of("sub", "ms-2", "preferred_username", "ana@empresa.co", "name", "Ana")), null).correo())
                .isEqualTo("ana@empresa.co");
        assertThat(SocialLogin.perfil("microsoft",
                deMicrosoft(Map.of("sub", "ms-3", "preferred_username", "+573001112233", "name", "Ana")), null).correo())
                .isNull();
    }
}
