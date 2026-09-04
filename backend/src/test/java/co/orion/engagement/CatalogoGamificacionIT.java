package co.orion.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import co.orion.TestcontainersConfiguration;
import co.orion.engagement.domain.Achievement;
import co.orion.engagement.domain.AchievementFamily;
import co.orion.engagement.domain.Cosmetic;
import co.orion.engagement.domain.CosmeticId;
import co.orion.engagement.domain.CosmeticKind;
import co.orion.engagement.persistence.AchievementRepository;
import co.orion.engagement.persistence.CosmeticRepository;

/**
 * El catálogo se siembra en la migración, así que lo que se comprueba aquí es que la semilla es la
 * del diseño y que las constraints hacen lo que dicen. Un catálogo mal sembrado no falla al
 * arrancar: falla meses después, cuando alguien no puede desbloquear una pieza.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogoGamificacionIT {

    @Autowired
    private AchievementRepository achievements;

    @Autowired
    private CosmeticRepository cosmetics;

    @Test
    void estanLosVeinteLogrosDelDiseno() {
        assertThat(achievements.findByActiveTrueOrderByDisplayOrderAsc()).hasSize(20);
    }

    @Test
    void cadaFamiliaTieneLosSuyos() {
        var porFamilia = achievements.findAll().stream()
                .collect(Collectors.groupingBy(Achievement::getFamily, Collectors.counting()));

        assertThat(porFamilia)
                .containsEntry(AchievementFamily.PRIMEROS, 4L)
                .containsEntry(AchievementFamily.CONSTANCIA, 5L)
                .containsEntry(AchievementFamily.VOLUMEN, 5L)
                .containsEntry(AchievementFamily.AMPLITUD, 3L)
                .containsEntry(AchievementFamily.COMPROMISO, 3L);
    }

    /** Los textos son los del diseño, aprobados con la voz de marca. No se reescriben. */
    @Test
    void losTextosSonLosDelDiseno() {
        assertThat(achievements.findById("constancia-8-semanas").orElseThrow().getDescription())
                .isEqualTo("8 semanas consecutivas. Sube el sello a nivel 2.");
        assertThat(achievements.findById("amplitud-dos-idiomas").orElseThrow().getDescription())
                .isEqualTo("Al menos una clase en un segundo idioma.");
    }

    /** Los de brillo 3 son los dos hitos grandes, y son los únicos que además mandan correo. */
    @Test
    void losHitosDeBrilloTresSonLosDosGrandes() {
        var brilloTres = achievements.findAll().stream()
                .filter(a -> a.getGlow() == 3)
                .map(Achievement::getCode)
                .toList();

        assertThat(brilloTres).containsExactlyInAnyOrder(
                "constancia-24-semanas", "volumen-100-clases");
    }

    /**
     * Ninguna pieza puede quedar inalcanzable: o es inicial o tiene su logro. Lo garantiza un
     * CHECK, y este test es el que avisaría si alguien lo relajara.
     */
    @Test
    void ningunCosmeticoQuedaInalcanzable() {
        assertThat(cosmetics.findAll())
                .allSatisfy(c -> assertThat(c.isDefaultPiece() || c.getUnlockAchievement() != null)
                        .as("cosmético %s", c.getCode())
                        .isTrue());
    }

    /** Un inicial por familia visible: nadie arranca con el avatar sin definir. */
    @Test
    void hayUnInicialPorFamilia() {
        for (CosmeticKind kind : List.of(CosmeticKind.FRAME, CosmeticKind.PALETTE, CosmeticKind.SKY)) {
            assertThat(cosmetics.findByKindOrderByDisplayOrderAsc(kind))
                    .as("iniciales de %s", kind)
                    .filteredOn(Cosmetic::isDefaultPiece)
                    .hasSize(1);
        }
    }

    /** Los tres accesorios llevan zona y los demás no. Lo dice el CHECK. */
    @Test
    void soloLosAccesoriosTienenZona() {
        assertThat(cosmetics.findByKindOrderByDisplayOrderAsc(CosmeticKind.ACCESSORY))
                .hasSize(3)
                .allSatisfy(c -> assertThat(c.getZone()).isNotNull());
        assertThat(cosmetics.findByKindOrderByDisplayOrderAsc(CosmeticKind.FRAME))
                .allSatisfy(c -> assertThat(c.getZone()).isNull());
    }

    /**
     * El diseño reutiliza «trazo» para marco y paleta, y «noche»/«amanecer» para paleta y cielo.
     * La clave compuesta lo permite; con una clave simple habría habido que inventar prefijos, y
     * entonces el valor por defecto de la ficha —que el propio diseño fija en «trazo»— habría
     * dejado de casar.
     */
    @Test
    void unMismoNombrePuedeSerDosPiezasDistintas() {
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.FRAME, "trazo"))).isPresent();
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.PALETTE, "trazo"))).isPresent();
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.PALETTE, "amanecer"))).isPresent();
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.SKY, "amanecer"))).isPresent();
    }

    /** Los cosméticos iniciales son exactamente los que la ficha trae por defecto. */
    @Test
    void losInicialesCoincidenConLosPorDefectoDeLaFicha() {
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.FRAME, "trazo")).orElseThrow()
                .isDefaultPiece()).isTrue();
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.PALETTE, "trazo")).orElseThrow()
                .isDefaultPiece()).isTrue();
        assertThat(cosmetics.findById(new CosmeticId(CosmeticKind.SKY, "crema")).orElseThrow()
                .isDefaultPiece()).isTrue();
    }
}
