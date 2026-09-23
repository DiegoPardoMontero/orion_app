package co.orion.teaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EditRatioTest {

    @Test
    @DisplayName("Publicada tal cual: 0")
    void talCual() {
        assertThat(EditRatio.entre("Trabajamos past simple.", "Trabajamos past simple.")).isZero();
    }

    @Test
    @DisplayName("Espacios y mayúsculas no son una edición")
    void espaciosYMayusculasNoCuentan() {
        assertThat(EditRatio.entre("Trabajamos  past simple.\n", "trabajamos past simple.")).isZero();
    }

    @Test
    @DisplayName("Reescrita entera: cerca de 1")
    void reescrita() {
        assertThat(EditRatio.entre("Trabajamos past simple y used to.", "Conversación libre sobre viajes."))
                .isGreaterThan(0.7);
    }

    @Test
    @DisplayName("Un retoque pequeño da una cifra pequeña")
    void retoque() {
        double r = EditRatio.entre("Trabajamos past simple y used to.", "Trabajamos past simple y 'used to'.");
        assertThat(r).isGreaterThan(0).isLessThan(0.1);
    }

    @Test
    @DisplayName("Sin borrador original no hay nada que medir")
    void vacios() {
        assertThat(EditRatio.entre(null, "")).isZero();
        assertThat(EditRatio.entre("", "Algo nuevo")).isEqualTo(1.0);
    }
}
