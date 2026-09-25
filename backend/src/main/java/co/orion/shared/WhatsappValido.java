package co.orion.shared;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Un número de WhatsApp al que se puede escribir, una vez normalizado con {@link PhoneNumbers}.
 *
 * <p>Deja pasar el vacío: que sea obligatorio lo dice {@code @NotBlank} al lado. Así cada anotación
 * responde por una sola cosa, y el admin puede crear una cuenta sin número (se lo pide la app a esa
 * persona al entrar) con la misma regla de formato.
 */
@Documented
@Constraint(validatedBy = WhatsappValido.Validador.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface WhatsappValido {

    String message() default "Escribe tu número de WhatsApp completo, con el indicativo del país.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<WhatsappValido, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            return valor == null || valor.isBlank() || PhoneNumbers.esWhatsappValido(PhoneNumbers.toE164(valor));
        }
    }
}
