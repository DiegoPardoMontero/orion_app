package co.orion.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;

class EmisorDeMicrosoftTest {

    /** El inquilino de las cuentas personales (Outlook, Hotmail, Live) es siempre este. */
    private static final String PERSONALES = "9188040d-6c67-4c5b-b112-36a304b66dad";

    private static Jwt token(Map<String, Object> reclamos) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        reclamos.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("Vale el emisor del inquilino que dice el propio token, personal o de empresa")
    void emisorDelInquilino() {
        assertThat(new EmisorDeMicrosoft().validate(token(Map.of("tid", PERSONALES,
                "iss", "https://login.microsoftonline.com/" + PERSONALES + "/v2.0"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("No vale un emisor de otro inquilino, ni un «tid» raro, ni la falta de alguno")
    void emisorAjeno() {
        EmisorDeMicrosoft emisor = new EmisorDeMicrosoft();
        String otro = "72f988bf-86f1-41af-91ab-2d7cd011db47";
        assertThat(emisor.validate(token(Map.of("tid", PERSONALES,
                "iss", "https://login.microsoftonline.com/" + otro + "/v2.0"))).hasErrors()).isTrue();
        assertThat(emisor.validate(token(Map.of("tid", "common",
                "iss", "https://login.microsoftonline.com/common/v2.0"))).hasErrors()).isTrue();
        assertThat(emisor.validate(token(Map.of("iss", "https://login.microsoftonline.com/" + PERSONALES + "/v2.0")))
                .hasErrors()).isTrue();
    }

    @Test
    @DisplayName("La comprobación del emisor solo se añade a Microsoft: Google sigue con la suya")
    void soloParaMicrosoft() {
        Jwt conEmisorAjeno = token(Map.of("sub", "s-1", "aud", List.of("cliente"), "tid", PERSONALES,
                "iss", "https://otro.sitio/v2.0"));

        assertThat(TokensDeIdentidad.validador(registro("microsoft")).validate(conEmisorAjeno).hasErrors()).isTrue();
        assertThat(TokensDeIdentidad.validador(registro("google")).validate(conEmisorAjeno).hasErrors()).isFalse();
    }

    private static ClientRegistration registro(String id) {
        return ClientRegistration.withRegistrationId(id).clientId("cliente").clientSecret("s")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://orion.test/login/oauth2/code/" + id)
                .authorizationUri("https://x/authorize").tokenUri("https://x/token").jwkSetUri("https://x/keys")
                .userNameAttributeName("sub").build();
    }
}
