package co.orion.practice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.practice.application.PracticeGenerator.Generado;
import co.orion.practice.domain.PracticeItemType;

/** Lo que salió contra OpenAI con el primer prompt v6: pistas que eran la respuesta. */
class PistasTest {

    private static Generado con(PracticeItemType tipo, String esperada, String pista) {
        return new Generado(tipo, "x", "{}", esperada, "y", null, pista);
    }

    @Test
    @DisplayName("Una pista que dice la respuesta se cambia por la genérica de su tipo")
    void laQueRegalaLaRespuesta() {
        assertThat(Pistas.segura(con(PracticeItemType.FILL_BLANK, "luggage", "luggage")))
                .isEqualTo("Relee la frase completa: ¿qué palabra de tu clase encaja ahí?");
        assertThat(Pistas.segura(con(PracticeItemType.FILL_BLANK, "luggage", "Piensa en luggage, lo que llevas.")))
                .startsWith("Relee");
        assertThat(Pistas.segura(con(PracticeItemType.SPOT_ERROR, "{\"index\":1,\"correction\":\"I am 30.\"}",
                "Cambia have → am en la frase."))).startsWith("Léela despacio");
        assertThat(Pistas.segura(con(PracticeItemType.MATCH_MEANING, "{\"layover\":\"escala\"}",
                "Recuerda que layover es escala en el viaje."))).startsWith("Esas dos no van juntas");
    }

    @Test
    @DisplayName("Una buena pista se queda; sin pista, la genérica")
    void laBuena() {
        String buena = "Es el papel que muestras en la puerta para subir al avión.";
        assertThat(Pistas.segura(con(PracticeItemType.FILL_BLANK, "boarding pass", buena))).isEqualTo(buena);
        assertThat(Pistas.segura(con(PracticeItemType.DICTATION, "Here is my pass.", null)))
                .isEqualTo("Escúchala otra vez, más despacio, palabra por palabra.");
    }
}
