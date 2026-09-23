package co.orion.shared.security;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Una contraseña que bcrypt pueda procesar: como mucho 72 <em>bytes</em>.
 *
 * <p>Más allá de eso bcrypt no mira, y Spring Security 7 ya no trunca en silencio: lanza, y la
 * excepción llegaba como un 500 —con alerta por correo incluida— a cualquiera que pegara un texto
 * largo en el login. Se cuentan bytes y no caracteres porque «ñ» son dos y un emoji cuatro:
 * {@code @Size(max = 72)} dejaría pasar una contraseña de 40 emojis que bcrypt no acepta.
 */
@Documented
@Constraint(validatedBy = CabeEnBcrypt.Validador.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface CabeEnBcrypt {

    String message() default "La contraseña es demasiado larga";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CabeEnBcrypt, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
}
