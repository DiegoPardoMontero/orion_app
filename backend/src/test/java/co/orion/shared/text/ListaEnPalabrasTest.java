package co.orion.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ListaEnPalabrasTest {

    @Test
    void laUltimaUnionEsConY() {
        assertThat(ListaEnPalabras.unir(List.of("tu foto"))).isEqualTo("tu foto");
        assertThat(ListaEnPalabras.unir(List.of("tu foto", "tu tarifa"))).isEqualTo("tu foto y tu tarifa");
        assertThat(ListaEnPalabras.unir(List.of("tu foto", "tu nivel", "el idioma")))
                .isEqualTo("tu foto, tu nivel y el idioma");
        assertThat(ListaEnPalabras.unir(List.of())).isEmpty();
    }

    @Test
    void unaSolaCosaEnSingularVaConFalta() {
        assertThat(ListaEnPalabras.teFalta(List.of("tu foto"))).isEqualTo("te falta tu foto");
        assertThat(ListaEnPalabras.teFalta(List.of("publicar tu perfil"))).isEqualTo("te falta publicar tu perfil");
    }

    /** «Te falta tus horarios» era el caso que se leía mal aun con una sola cosa. */
    @Test
    void unaSolaCosaEnPluralVaConFaltan() {
        assertThat(ListaEnPalabras.teFalta(List.of("tus horarios"))).isEqualTo("te faltan tus horarios");
        assertThat(ListaEnPalabras.teFalta(List.of("los idiomas que enseñas")))
                .isEqualTo("te faltan los idiomas que enseñas");
    }

    @Test
    void variasCosasVanConFaltan() {
        assertThat(ListaEnPalabras.teFalta(List.of("tu foto", "tu tarifa"))).isEqualTo("te faltan tu foto y tu tarifa");
        assertThat(ListaEnPalabras.falta(List.of("tu foto", "tu tarifa"))).isEqualTo("faltan");
    }

    @Test
    void alComienzoDeLaFraseVaEnMayuscula() {
        assertThat(ListaEnPalabras.teFaltaAlComienzo(List.of("tu foto", "el idioma")))
                .isEqualTo("Te faltan tu foto y el idioma");
    }
}
