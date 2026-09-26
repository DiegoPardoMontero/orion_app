package co.orion.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FestivosColombiaTest {

    /** Los 18 festivos de 2026, como los publica el calendario oficial. */
    @Test
    void losFestivosDe2026() {
        assertThat(FestivosColombia.delAnio(2026)).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
    }

    @Test
    void laPascua() {
        assertThat(FestivosColombia.pascua(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(FestivosColombia.pascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(FestivosColombia.pascua(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    /** El 16 de noviembre de 2026 es festivo: el tercer día hábil desde ahí salta al jueves 19. */
    @Test
    void elTercerDiaHabilSaltaFinesDeSemanaYFestivos() {
        assertThat(FestivosColombia.enesimoHabilDesde(LocalDate.of(2026, 11, 16), 3)).isEqualTo(LocalDate.of(2026, 11, 19));
        // Desde un jueves hábil: jueves, viernes, lunes.
        assertThat(FestivosColombia.enesimoHabilDesde(LocalDate.of(2026, 10, 1), 3)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(FestivosColombia.esHabil(LocalDate.of(2026, 8, 17))).isFalse();
    }
}
