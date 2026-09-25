package co.orion.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los tramos del resultado: nombres del handoff, nunca letras del MCER. */
class EtiquetaDelPuntajeTest {

    @Test
    @DisplayName("Cada tramo tiene su nombre, con los bordes donde se dijo")
    void losTramos() {
        assertThat(EtiquetaDelPuntaje.de(0)).isEqualTo("Primeros pasos");
        assertThat(EtiquetaDelPuntaje.de(24)).isEqualTo("Primeros pasos");
        assertThat(EtiquetaDelPuntaje.de(25)).isEqualTo("Básico con ganas");
        assertThat(EtiquetaDelPuntaje.de(45)).isEqualTo("Ya te defiendes");
        assertThat(EtiquetaDelPuntaje.de(65)).isEqualTo("Con soltura");
        assertThat(EtiquetaDelPuntaje.de(85)).isEqualTo("Casi sin pensarlo");
        assertThat(EtiquetaDelPuntaje.de(100)).isEqualTo("Casi sin pensarlo");
    }

    @Test
    @DisplayName("Sin número, la etiqueta es la de quien empieza")
    void sinNumero() {
        assertThat(EtiquetaDelPuntaje.de(null)).isEqualTo("Primeros pasos");
    }
}
