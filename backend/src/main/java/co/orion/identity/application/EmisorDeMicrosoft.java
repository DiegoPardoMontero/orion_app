package co.orion.identity.application;

import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * El emisor del token de identidad de Microsoft, comprobado a mano.
 *
 * <p>Con el extremo «common» cada token lo emite el inquilino de la persona —el de cuentas
 * personales, o el de su empresa o universidad—, así que no hay un único emisor que fijar y el
 * validador de Spring no lo compara. Sin esto, cualquier token firmado por Microsoft para nuestra
 * aplicación valdría con cualquier «iss». La regla es la de Microsoft para aplicaciones
 * multiinquilino: el emisor tiene que ser exactamente el del inquilino que dice el propio token.
 */
public final class EmisorDeMicrosoft implements OAuth2TokenValidator<Jwt> {

    private static final Pattern INQUILINO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String inquilino = token.getClaimAsString("tid");
        Object emisor = token.getClaims().get("iss");
        if (inquilino != null && INQUILINO.matcher(inquilino).matches() && emisor != null
                && ("https://login.microsoftonline.com/" + inquilino + "/v2.0").equals(emisor.toString())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_id_token", "El emisor no corresponde al inquilino de Microsoft", null));
    }
}
