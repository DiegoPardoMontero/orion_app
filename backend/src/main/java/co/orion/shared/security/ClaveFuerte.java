package co.orion.shared.security;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Una contraseña «Fuerte» o «Excelente» (Pardo, 27/09/2026), con la misma regla que el medidor del
 * registro ({@code frontend/src/lib/password.ts}): además de los 8 caracteres, al menos dos de estas
 * tres cosas —mayúsculas y minúsculas, un número, un símbolo—. La pantalla ya no deja enviar una
 * débil; esto es para que tampoco entre llamando a la API directamente.
 *
 * <p>El largo no lo mira: de eso se encarga el {@code @Size(min = 8)} de cada campo, con su propio
 * mensaje. Si lo miraran los dos, una clave corta sumaría dos errores y el usuario leería el genérico
 * «Revisa los datos» en vez de «al menos 8 caracteres».
 *
 * <p>Solo cuida las contraseñas que la persona escoge (registro, restablecer y cambiar). Las que ya
 * existen siguen sirviendo para entrar, y las que genera el admin —tres palabras y un número con
 * guiones— ya son «Fuertes».
 */
@Documented
@Constraint(validatedBy = ClaveFuerte.Validador.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ClaveFuerte {

    String message() default "Tu contraseña es débil: usa al menos dos de estas tres cosas: "
            + "mayúsculas y minúsculas, un número, un símbolo.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ClaveFuerte, String> {

        static final int MINIMO = 8;
        private static final Pattern MAYUSCULA = Pattern.compile("[A-Z]");
        private static final Pattern MINUSCULA = Pattern.compile("[a-z]");
        private static final Pattern NUMERO = Pattern.compile("[0-9]");
        private static final Pattern SIMBOLO = Pattern.compile("[^A-Za-z0-9]");

        @Override
        public boolean isValid(String clave, ConstraintValidatorContext context) {
            if (clave == null || clave.length() < MINIMO) {
                return true;
            }
            int puntos = 0;
            if (MAYUSCULA.matcher(clave).find() && MINUSCULA.matcher(clave).find()) puntos++;
            if (NUMERO.matcher(clave).find()) puntos++;
            if (SIMBOLO.matcher(clave).find()) puntos++;
            return puntos >= 2;
        }
    }
}
