package co.orion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los mismos casos que {@code esFuerte} en {@code frontend/src/lib/password.test.ts}. */
class ClaveFuerteTest {

    private final ClaveFuerte.Validador validador = new ClaveFuerte.Validador();

    private boolean pasa(String clave) {
        return validador.isValid(clave, null);
    }

    @Test
    @DisplayName("«Fuerte» y «Excelente» pasan; «Vas bien» y «Débil» no")
    void soloFuerteOExcelente() {
        assertThat(pasa("Abcdefg1")).isTrue();       // mayúsc/minúsc + número
        assertThat(pasa("orion123*")).isTrue();      // número + símbolo
        assertThat(pasa("Abcdefg!")).isTrue();       // mayúsc/minúsc + símbolo
        assertThat(pasa("Abcdefg1!")).isTrue();      // las tres
        assertThat(pasa("aurora-brisa-cielo-42")).isTrue(); // la que genera el admin

        assertThat(pasa("Abcdefgh")).isFalse();      // solo mayúsc/minúsc
        assertThat(pasa("abcdefg1")).isFalse();      // solo número
        assertThat(pasa("abcdefgh")).isFalse();      // nada más que el largo
        assertThat(pasa("ABCDEFG1")).isFalse();      // sin minúscula no cuenta la mezcla
    }

    @Test
    @DisplayName("El largo y el vacío los deja a @Size y @NotBlank, para no sumar dos errores")
    void elLargoNoEsCosaSuya() {
        assertThat(pasa(null)).isTrue();
        assertThat(pasa("")).isTrue();
        assertThat(pasa("corta")).isTrue();
    }
}
