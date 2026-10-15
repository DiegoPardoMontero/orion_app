package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Todo opcional: es un PATCH, solo se toca lo que viene. */
public record UpdateUserRequest(
        @Size(max = 150) String fullName,
        @Size(max = 20) String whatsappPhone,
        String status,
        /**
         * El correo corregido, solo para una cuenta cuyo correo nadie ha confirmado (14/10/2026): el
         * profe que el admin creó con el correo mal escrito no podía confirmarlo ni publicar nunca.
         */
        @Email @Size(max = 255) String email) {

    /** Sin cambio de correo: el PATCH de siempre. */
    public UpdateUserRequest(String fullName, String whatsappPhone, String status) {
        this(fullName, whatsappPhone, status, null);
    }
}
