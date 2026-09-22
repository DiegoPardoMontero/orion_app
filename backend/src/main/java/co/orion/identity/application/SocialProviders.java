package co.orion.identity.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * Google, Facebook y Apple: cuáles están configurados en este despliegue y cómo se habla con cada uno.
 *
 * <p><strong>Cada proveedor existe solo si tiene sus credenciales</strong> (Pardo, 22/09/2026:
 * «Google primero»). Sin variables, el proveedor no se registra, su botón no aparece y Administración
 * → Sistema lo muestra apagado. Por eso este repositorio puede estar vacío —el de Spring exige al
 * menos uno— y en ese caso SecurityConfig ni siquiera enciende el login social.
 *
 * <p><strong>La dirección de vuelta es la del frontend</strong> ({@code orion.app.base-url}), no la
 * del backend: el navegador solo conoce el dominio público, y Next reenvía
 * {@code /login/oauth2/**} a esta aplicación. Es la misma URL que hay que dar de alta en la consola
 * de cada proveedor.
 */
@Component
public class SocialProviders implements ClientRegistrationRepository {

    public static final String APPLE = "apple";

    private final Map<String, ClientRegistration> registros = new LinkedHashMap<>();
    private final AppleClientSecret appleSecret;
    private final String appleClientId;

    public SocialProviders(
            @Value("${orion.app.base-url}") String baseUrl,
            @Value("${orion.social.google.client-id:}") String googleId,
            @Value("${orion.social.google.client-secret:}") String googleSecret,
            @Value("${orion.social.facebook.client-id:}") String facebookId,
            @Value("${orion.social.facebook.client-secret:}") String facebookSecret,
            @Value("${orion.social.apple.client-id:}") String appleId,
            @Value("${orion.social.apple.team-id:}") String appleTeam,
            @Value("${orion.social.apple.key-id:}") String appleKey,
            @Value("${orion.social.apple.private-key:}") String applePem,
            Clock clock) {
        String vuelta = baseUrl + "/login/oauth2/code/{registrationId}";

        if (llenos(googleId, googleSecret)) {
            registros.put("google", CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId).clientSecret(googleSecret)
                    .redirectUri(vuelta)
                    .scope("openid", "email", "profile")
                    .build());
        }
        if (llenos(facebookId, facebookSecret)) {
            registros.put("facebook", CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
                    .clientId(facebookId).clientSecret(facebookSecret)
                    .redirectUri(vuelta)
                    .scope("public_profile", "email")
                    .build());
        }
        if (llenos(appleId, appleTeam, appleKey, applePem)) {
            // El secreto de Apple se firma en cada intercambio (ver AppleClientSecret); aquí va un
            // marcador que el cliente de tokens sustituye antes de enviar.
            registros.put(APPLE, ClientRegistration.withRegistrationId(APPLE)
                    .clientId(appleId).clientSecret("se-firma-en-cada-intercambio")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(vuelta)
                    .scope("openid", "name", "email")
                    .authorizationUri("https://appleid.apple.com/auth/authorize")
                    .tokenUri("https://appleid.apple.com/auth/token")
                    .jwkSetUri("https://appleid.apple.com/auth/keys")
                    .issuerUri("https://appleid.apple.com")
                    .userNameAttributeName("sub")
                    .clientName("Apple")
                    .build());
            this.appleSecret = new AppleClientSecret(appleTeam, appleKey, appleId, applePem, clock);
            this.appleClientId = appleId;
        } else {
            this.appleSecret = null;
            this.appleClientId = null;
        }
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registros.get(registrationId);
    }

    /** Los ids de los que están encendidos, en el orden en que se muestran los botones. */
    public List<String> configurados() {
        return List.copyOf(registros.keySet());
    }

    public boolean algunoConfigurado() {
        return !registros.isEmpty();
    }

    public boolean appleConfigurado() {
        return appleSecret != null;
    }

    /** El secreto de Apple recién firmado. Solo tiene sentido si Apple está configurado. */
    public String secretoDeApple() {
        return appleSecret.firmar();
    }

    public String appleClientId() {
        return appleClientId;
    }

    private static boolean llenos(String... valores) {
        for (String v : valores) {
            if (v == null || v.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
