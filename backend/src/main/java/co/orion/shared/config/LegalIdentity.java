package co.orion.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Quién responde por Orión: el responsable del tratamiento de datos (Ley 1581 de 2012) y el
 * proveedor frente al consumidor (art. 50 de la Ley 1480 de 2011, que exige publicar identidad,
 * domicilio de notificaciones, teléfono y correo).
 *
 * <p>Estos datos aparecen en los términos, en la política de tratamiento y en la pantalla de
 * ayuda. Viven aquí y no en el texto porque cambiar de dirección no puede depender de acordarse
 * de editar cinco archivos.
 *
 * <p>En {@code prod} ninguno tiene valor por defecto: si falta uno, la aplicación no arranca. Es
 * deliberado — un Orión publicado sin domicilio de notificaciones incumple, y es mejor que no
 * levante a que levante mintiendo.
 */
@ConfigurationProperties(prefix = "orion.legal")
@Validated
public record LegalIdentity(

        /** Nombre completo de la persona natural responsable. */
        @NotBlank String responsable,

        /** Documento de identidad, tal como debe aparecer publicado: "C.C. 1.234.567.890". */
        @NotBlank String documento,

        /** Dirección física para notificaciones judiciales. */
        @NotBlank String domicilio,

        @NotBlank String ciudad,

        /** Correo de contacto para habeas data y PQR. */
        @NotBlank String correo,

        /** WhatsApp de soporte en formato E.164: +573001112233. */
        @NotBlank
        @Pattern(regexp = "\\+[1-9]\\d{7,14}", message = "el WhatsApp debe ir en formato E.164")
        String whatsapp,

        /** Horario de atención, en el español que va a leer una persona. */
        @NotBlank String horario) {

    /** El número sin el "+" ni separadores, que es como lo quiere un enlace wa.me. */
    public String whatsappDigits() {
        return whatsapp.replaceAll("\\D", "");
    }
}
