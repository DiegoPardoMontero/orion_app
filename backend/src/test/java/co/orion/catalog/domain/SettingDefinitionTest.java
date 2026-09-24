package co.orion.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.shared.error.UnprocessableException;

/**
 * La validación de los ajustes.
 *
 * <p>Antes del Bloque 9, {@code PUT /admin/settings/{key}} aceptaba cualquier texto. Un cero de más
 * en la comisión cambia lo que cobra cada reserva desde ese instante, y un {@code sanctions_mode}
 * mal escrito habría roto la evaluación de sanciones en silencio — la peor clase de avería, porque
 * nada se cae y todo está mal.
 */
class SettingDefinitionTest {

    @Test
    @DisplayName("La comisión admite su rango y rechaza lo que está fuera")
    void laComisionTieneRango() {
        assertThat(SettingDefinition.COMMISSION_RATE_BPS.validate("2000")).isEqualTo("2000");
        assertThat(SettingDefinition.COMMISSION_RATE_BPS.validate("0")).isEqualTo("0");

        assertThatThrownBy(() -> SettingDefinition.COMMISSION_RATE_BPS.validate("20000"))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("entre 0 y 5000");
    }

    /** El error tiene que decir qué se admite: rechazar sin decir el rango deja adivinando. */
    @Test
    @DisplayName("Un texto donde va un número dice que hace falta un entero")
    void unTextoDondeVaUnNumero() {
        assertThatThrownBy(() -> SettingDefinition.PAYMENT_HOLD_MINUTES.validate("veinte"))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("número entero");
    }

    @Test
    @DisplayName("Los enumerados solo admiten sus valores, y los normalizan a mayúsculas")
    void losEnumeradosSoloAdmitenLoSuyo() {
        assertThat(SettingDefinition.SANCTIONS_MODE.validate("enforce")).isEqualTo("ENFORCE");

        assertThatThrownBy(() -> SettingDefinition.SANCTIONS_MODE.validate("APLICAR"))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("ENFORCE")
                .hasMessageContaining("OBSERVE");
    }

    @Test
    @DisplayName("Los booleanos se normalizan y rechazan cualquier otra cosa")
    void losBooleanos() {
        assertThat(SettingDefinition.GAMIFICATION_COUNT_FREE_LESSONS.validate("TRUE"))
                .isEqualTo("true");

        assertThatThrownBy(() -> SettingDefinition.GAMIFICATION_COUNT_FREE_LESSONS.validate("sí"))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    @DisplayName("Un valor vacío no pasa, salvo en un enlace (ver losEnlaces)")
    void elVacioNoPasa() {
        assertThatThrownBy(() -> SettingDefinition.AUTO_COMPLETE_HOURS.validate("   "))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("vacío");
    }

    /**
     * Las dos ventanas de cancelación son la misma promesa en los Términos («12 h para ambos»).
     * Que las dos estén marcadas como sensibles es lo que obliga a confirmar por escrito al tocar
     * una sola y quedarse a medias.
     */
    @Test
    @DisplayName("Lo que mueve dinero o plazos está marcado como sensible")
    void loQueMueveDineroEsSensible() {
        assertThat(SettingDefinition.COMMISSION_RATE_BPS.isSensitive()).isTrue();
        assertThat(SettingDefinition.STUDENT_CANCEL_HOURS.isSensitive()).isTrue();
        assertThat(SettingDefinition.PROFESSOR_CANCEL_HOURS.isSensitive()).isTrue();
        assertThat(SettingDefinition.AUTO_COMPLETE_HOURS.isSensitive()).isTrue();
        assertThat(SettingDefinition.SANCTIONS_MODE.isSensitive()).isTrue();

        assertThat(SettingDefinition.RANKING_WEIGHT_RATING.isSensitive()).isFalse();
    }

    @Test
    @DisplayName("Un enlace vacío es «no hay»; si viene, tiene que ser https con dominio")
    void losEnlaces() {
        SettingDefinition video = SettingDefinition.PROFESSOR_WELCOME_VIDEO_URL;
        assertThat(video.validate("  ")).isEmpty();
        assertThat(video.validate(" https://youtu.be/abc ")).isEqualTo("https://youtu.be/abc");
        assertThatThrownBy(() -> video.validate("javascript:alert(1)")).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> video.validate("http://youtu.be/abc")).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> video.validate("https:///sin-dominio")).isInstanceOf(UnprocessableException.class);
        assertThatThrownBy(() -> video.validate("https://youtu.be/con espacio")).isInstanceOf(UnprocessableException.class);
    }

    @Test
    @DisplayName("Toda definición tiene etiqueta y explicación en español")
    void todasSeExplican() {
        for (SettingDefinition d : SettingDefinition.values()) {
            assertThat(d.getEtiqueta()).as(d.getKey() + " sin etiqueta").isNotBlank();
            assertThat(d.getExplicacion()).as(d.getKey() + " sin explicación").isNotBlank();
        }
    }
}
