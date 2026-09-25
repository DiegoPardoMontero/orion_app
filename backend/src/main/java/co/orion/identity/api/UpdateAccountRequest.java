package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import co.orion.shared.WhatsappValido;

/** Lo único que el usuario cambia de su cuenta: su nombre y su WhatsApp, que se cambia pero no se borra. */
public record UpdateAccountRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank(message = "Tu WhatsApp es obligatorio.") @Size(max = 20) @WhatsappValido String whatsappPhone) {
}
