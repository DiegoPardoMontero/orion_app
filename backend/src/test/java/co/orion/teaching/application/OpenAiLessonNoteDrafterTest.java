package co.orion.teaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.catalog.application.PlatformSettingsService;

/**
 * Lo que decide si el borrador de la IA sirve: solo pasa un JSON con las cuatro claves, dentro de
 * sus límites y sin juicios sobre el estudiante. Todo lo demás cae al camino a mano.
 */
class OpenAiLessonNoteDrafterTest {

    @Test
    @DisplayName("Un JSON con las cuatro claves pasa, con su vocabulario")
    void elBuenoPasa() {
        var b = OpenAiLessonNoteDrafter.validar("""
                {"workedOn":"Past simple.","recurringIssues":"Dice 'I go yesterday'.",
                 "nextSteps":"Condicionales.","vocabulary":[{"term":"used to","meaning":"solía"}]}""");

        assertThat(b).isPresent();
        assertThat(b.get().vocabulario()).singleElement().extracting(LessonNoteDrafter.Palabra::term)
                .isEqualTo("used to");
    }

    @Test
    @DisplayName("Una palabra repetida al revés (la traducción como palabra nueva) se queda una sola vez")
    void sinParesInvertidos() {
        var b = OpenAiLessonNoteDrafter.validar("""
                {"workedOn":"El menú.","recurringIssues":"","nextSteps":"",
                 "vocabulary":[{"term":"appetizer","meaning":"entrada"},{"term":"entrada","meaning":"appetizer"},
                               {"term":"bill","meaning":"cuenta"},{"term":"Bill","meaning":"la cuenta"}]}""");

        assertThat(b).isPresent();
        assertThat(b.get().vocabulario()).extracting(LessonNoteDrafter.Palabra::term).containsExactly("appetizer", "bill");
    }

    @Test
    @DisplayName("Lo que no es JSON, o trae claves de más, no pasa")
    void loRaroNoPasa() {
        assertThat(OpenAiLessonNoteDrafter.validar("Claro, aquí tienes el acta: ...")).isEmpty();
        assertThat(OpenAiLessonNoteDrafter.validar("""
                {"workedOn":"a","recurringIssues":"b","nextSteps":"c","level":"B1"}""")).isEmpty();
        assertThat(OpenAiLessonNoteDrafter.validar("[1,2,3]")).isEmpty();
    }

    @Test
    @DisplayName("Más de doce palabras nuevas, o una sección demasiado larga, no pasa")
    void losLimites() {
        String trece = "[" + "{\"term\":\"w\"},".repeat(12) + "{\"term\":\"w\"}]";
        assertThat(OpenAiLessonNoteDrafter.validar("""
                {"workedOn":"a","recurringIssues":"b","nextSteps":"c","vocabulary":%s}""".formatted(trece)))
                .isEmpty();
        assertThat(OpenAiLessonNoteDrafter.validar("""
                {"workedOn":"%s","recurringIssues":"b","nextSteps":"c"}""".formatted("x".repeat(1201))))
                .isEmpty();
    }

    @Test
    @DisplayName("Un juicio sobre el estudiante no llega a publicarse")
    void elJuicioNoPasa() {
        assertThat(OpenAiLessonNoteDrafter.validar("""
                {"workedOn":"Past simple.","recurringIssues":"Tu nivel es muy malo.","nextSteps":"c"}"""))
                .isEmpty();
    }

    @Test
    @DisplayName("Sin llave o sin presupuesto no se llama a nadie: camino a mano")
    void sinPresupuestoNoLlama() {
        TeachingAiBudget presupuesto = mock(TeachingAiBudget.class);
        when(presupuesto.disponible()).thenReturn(false);
        var contexto = new LessonNoteDrafter.Contexto(null, "notas de la clase de hoy", "Ana", "inglés", null, null);

        assertThat(new OpenAiLessonNoteDrafter("sk-test", "gpt-5-mini", presupuesto,
                mock(PlatformSettingsService.class), "http://localhost:9/no").redactar(contexto)).isEmpty();
        assertThat(new OpenAiLessonNoteDrafter("", "gpt-5-mini", mock(TeachingAiBudget.class),
                mock(PlatformSettingsService.class), "http://localhost:9/no").redactar(contexto)).isEmpty();
    }
}
