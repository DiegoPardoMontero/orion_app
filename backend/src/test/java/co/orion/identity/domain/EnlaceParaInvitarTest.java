package co.orion.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnlaceParaInvitarTest {

    @Test
    void sinTildesNiEspacios() {
        assertThat(EnlaceParaInvitar.slugDe("María José Gómez")).isEqualTo("maria-jose-gomez");
        assertThat(EnlaceParaInvitar.slugDe("  Ñoño   Peña-Ruiz ")).isEqualTo("nono-pena-ruiz");
    }

    @Test
    void largoCortadoEnUnGuion() {
        String slug = EnlaceParaInvitar.slugDe("Alejandra Valentina Montenegro de la Torre Villalobos");
        assertThat(slug).hasSizeLessThanOrEqualTo(EnlaceParaInvitar.MAXIMO).doesNotEndWith("-").startsWith("alejandra-valentina");
    }

    @Test
    void sinNadaUtilEsProfe() {
        assertThat(EnlaceParaInvitar.slugDe("   ")).isEqualTo("profe");
        assertThat(EnlaceParaInvitar.slugDe(null)).isEqualTo("profe");
    }
}
