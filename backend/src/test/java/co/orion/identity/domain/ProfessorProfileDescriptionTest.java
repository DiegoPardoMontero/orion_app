package co.orion.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import co.orion.shared.error.UnprocessableException;

/**
 * Los mínimos de la ficha pública viven en el dominio y no solo en el servicio: hay cuatro caminos
 * que escriben titular y descripción, y esta es la puerta por la que pasan todos.
 */
class ProfessorProfileDescriptionTest {

    private static final String TITULAR_VALIDO = "Profesora de inglés conversacional para adultos";
    private static final String BIO_VALIDA =
            "Enseño inglés conversacional a adultos que ya estudiaron el idioma alguna vez y aun "
                    + "así no se atreven a hablarlo. Practicamos desde la primera clase.";

    private ProfessorProfile perfil() {
        return new ProfessorProfile(new User("ana@orion.test", "hash", "Ana Ramírez", UserRole.PROFESSOR));
    }

    @Test
    void unTitularYUnaDescripcionSuficientesSeGuardan() {
        ProfessorProfile perfil = perfil();

        perfil.describe(TITULAR_VALIDO, BIO_VALIDA);

        assertThat(perfil.getHeadline()).isEqualTo(TITULAR_VALIDO);
        assertThat(perfil.getBio()).isEqualTo(BIO_VALIDA);
    }

    /**
     * Un 422 y no una excepción de programación: escribir poco no es un fallo del código sino algo
     * que el profesor corrige escribiendo más, y el mensaje es lo que va a leer en el formulario.
     */
    @Test
    void unTitularDemasiadoCortoSeRechaza() {
        assertThatThrownBy(() -> perfil().describe("Profesor de inglés", BIO_VALIDA))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("titular")
                .hasMessageContaining("3 palabras");
    }

    @Test
    void unaDescripcionDemasiadoCortaSeRechaza() {
        assertThatThrownBy(() -> perfil().describe(TITULAR_VALIDO, "Doy clases de inglés."))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("descripción");
    }

    @Test
    void unaDescripcionDemasiadoLargaTambien() {
        String demasiado = "palabra ".repeat(ProfessorProfile.MAX_PALABRAS_BIO + 1);

        assertThatThrownBy(() -> perfil().describe(TITULAR_VALIDO, demasiado))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("máximo");
    }

    /**
     * Vacío no es "poco": un perfil recién creado todavía no ha escrito nada, y obligarle a redactar
     * antes de poder guardar cualquier otro campo dejaría el formulario sin salida.
     */
    @Test
    void vacioSigueValiendo() {
        assertThatCode(() -> perfil().describe(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> perfil().describe("", "   ")).doesNotThrowAnyException();
    }

    @Test
    void contarPalabrasIgnoraElEspacioSobrante() {
        assertThat(ProfessorProfile.contarPalabras("  hola   mundo  ")).isEqualTo(2);
        assertThat(ProfessorProfile.contarPalabras("")).isZero();
        assertThat(ProfessorProfile.contarPalabras(null)).isZero();
    }
}
