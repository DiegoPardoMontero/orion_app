package co.orion.identity.domain;

/** Los proveedores con los que se puede entrar. El nombre en minúscula es su id de registro. */
public enum SocialProvider {
    GOOGLE,
    FACEBOOK,
    APPLE;

    public String registrationId() {
        return name().toLowerCase();
    }

    public static SocialProvider fromRegistrationId(String id) {
        return valueOf(id.toUpperCase());
    }
}
