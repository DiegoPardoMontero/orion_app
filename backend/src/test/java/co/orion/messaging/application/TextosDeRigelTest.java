package co.orion.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import co.orion.messaging.domain.RigelKind;

class TextosDeRigelTest {

    @ParameterizedTest
    @EnumSource(RigelKind.class)
    @DisplayName("Cada mensaje tiene título, texto y al menos un botón, y los botones no salen de Orión")
    void todosCompletos(RigelKind tipo) {
        TextosDeRigel.Mensaje m = TextosDeRigel.de(tipo, "Ana", Map.of());
        assertThat(m.titulo()).isNotBlank();
        assertThat(m.cuerpo()).isNotBlank();
        assertThat(m.botones()).isNotEmpty().allSatisfy(b -> {
            assertThat(b.etiqueta()).isNotBlank();
            assertThat(b.ruta()).startsWith("/").doesNotStartWith("//");
        });
    }

    @Test
    @DisplayName("Sin nombre de la otra persona, dice «tu profe» o «tu estudiante»")
    void sinNombres() {
        assertThat(TextosDeRigel.de(RigelKind.FIRST_BOOKING_STUDENT, "Ana", Map.of()).cuerpo())
                .contains("Reservaste con tu profe");
        assertThat(TextosDeRigel.de(RigelKind.FIRST_BOOKING_PROFESSOR, "María", Map.of()).cuerpo())
                .startsWith("tu estudiante reservó contigo");
        assertThat(TextosDeRigel.de(RigelKind.WELCOME_STUDENT, "", Map.of()).titulo()).isEqualTo("¡Hola! Soy Rigel ✨");
    }

    @Test
    @DisplayName("«¿Seguimos?» lleva al perfil del último profe cuando lo sabe")
    void seguimos() {
        TextosDeRigel.Mensaje con = TextosDeRigel.de(RigelKind.COME_BACK, "Ana",
                Map.of("profesor", "María", "profesorId", "abc"));
        assertThat(con.botones().get(0).ruta()).isEqualTo("/profesores/abc");
        assertThat(con.botones().get(0).etiqueta()).isEqualTo("Reservar con María");
        assertThat(TextosDeRigel.de(RigelKind.COME_BACK, "Ana", Map.of()).botones().get(0).ruta())
                .isEqualTo("/profesores");
    }
}
