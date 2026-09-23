package co.orion.identity.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * Cómo se validan los tokens de identidad de los proveedores. El login de Spring toma este bean si
 * existe: es lo de siempre —vigencia y las reglas de OpenID Connect— y, para Microsoft, además su
 * emisor por inquilino ({@link EmisorDeMicrosoft}).
 */
@Configuration
class TokensDeIdentidad {

    @Bean
    JwtDecoderFactory<ClientRegistration> decodificadorDeTokensDeIdentidad() {
        OidcIdTokenDecoderFactory fabrica = new OidcIdTokenDecoderFactory();
        fabrica.setJwtValidatorFactory(TokensDeIdentidad::validador);
        return fabrica;
    }

    static OAuth2TokenValidator<Jwt> validador(ClientRegistration registro) {
        OAuth2TokenValidator<Jwt> base = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new OidcIdTokenValidator(registro));
        return SocialProviders.MICROSOFT.equals(registro.getRegistrationId())
                ? new DelegatingOAuth2TokenValidator<>(base, new EmisorDeMicrosoft())
                : base;
    }
}
