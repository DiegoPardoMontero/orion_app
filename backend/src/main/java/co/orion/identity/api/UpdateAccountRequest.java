package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo único que el usuario cambia de su cuenta: su nombre y su WhatsApp. */
public record UpdateAccountRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String whatsappPhone) {
}
