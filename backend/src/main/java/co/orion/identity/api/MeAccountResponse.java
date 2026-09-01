package co.orion.identity.api;

import co.orion.identity.domain.User;

/** Los datos de cuenta que el propio usuario ve y edita. El email y el rol se muestran, no se tocan. */
public record MeAccountResponse(String fullName, String email, String whatsappPhone, String role) {

    public static MeAccountResponse from(User user) {
        return new MeAccountResponse(
                user.getFullName(),
                user.getEmail(),
                user.getWhatsappPhone(),
                user.getRole().name());
    }
}
