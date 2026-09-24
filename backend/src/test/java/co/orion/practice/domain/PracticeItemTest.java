package co.orion.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.shared.error.UnprocessableException;

/** Un ejercicio: cuándo es perfecto, qué costó y cómo se unen las parejas de a una. */
class PracticeItemTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T15:00:00Z");

    private static PracticeItem de(PracticeItemType tipo) {
        return new PracticeItem(UUID.randomUUID(), 0, tipo, "x", "{}", "a", "b", null);
    }

    private static PracticeItem acertado(int intentos) {
        PracticeItem i = de(PracticeItemType.FILL_BLANK);
        for (int k = 1; k < intentos; k++) {
            i.responder("no", false, 2, AHORA);
        }
        i.responder("a", true, 2, AHORA);
        return i;
    }

    @Test
    @DisplayName("Perfecta: todos al primer intento; uno al segundo, o uno saltado, la apagan")
    void perfecta() {
        assertThat(PracticeItem.perfecta(List.of(acertado(1), acertado(1)))).isTrue();
        assertThat(PracticeItem.perfecta(List.of(acertado(1), acertado(2)))).isFalse();
        PracticeItem saltado = de(PracticeItemType.DICTATION);
        saltado.saltar(2, AHORA);
        assertThat(PracticeItem.perfecta(List.of(acertado(1), saltado))).isFalse();
    }

    @Test
    @DisplayName("Costó lo resuelto al segundo intento o mostrado; lo saltado, no")
    void costo() {
        assertThat(acertado(1).costo(2)).isFalse();
        assertThat(acertado(2).costo(2)).isTrue();
        PracticeItem mostrado = de(PracticeItemType.FILL_BLANK);
        mostrado.responder("no", false, 2, AHORA);
        mostrado.responder("tampoco", false, 2, AHORA);
        assertThat(mostrado.costo(2)).isTrue();
        PracticeItem saltado = de(PracticeItemType.LISTEN_CHOOSE);
        saltado.saltar(2, AHORA);
        assertThat(saltado.costo(2)).isFalse();
    }

    @Test
    @DisplayName("Parejas: un par que va no gasta intento; uno que no, sí; todas unidas es acierto")
    void parejas() {
        PracticeItem p = de(PracticeItemType.MATCH_MEANING);
        p.pareja(true, false, "{\"a\":\"1\"}", null, 2, AHORA);
        assertThat(p.getAttempts()).isZero();
        p.pareja(false, false, "{\"a\":\"1\"}", "{\"b\":\"3\"}", 2, AHORA);
        assertThat(p.getAttempts()).isEqualTo(1);
        assertThat(p.getFirstAnswer()).isEqualTo("{\"b\":\"3\"}");
        p.pareja(true, true, "{\"a\":\"1\",\"b\":\"2\"}", null, 2, AHORA);
        assertThat(p.getCorrect()).isTrue();
        assertThat(p.alSegundoIntento()).isTrue();
        assertThatThrownBy(() -> p.pareja(true, true, "{}", null, 2, AHORA)).isInstanceOf(UnprocessableException.class);
    }
}
