package co.orion.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Los números que se escriben en pantallas y documentos salen de los ajustes, no del código.
 *
 * <p>Es la regla de Pardo hecha prueba: lo que se cambia desde Ajustes tiene que cambiar en todas
 * partes. Que alguien vuelva a teclear «12 horas» en una plantilla lo caza la revisión, no esto;
 * pero esto sí garantiza que quien pregunte reciba el número de la base y con el plural resuelto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicFiguresServiceTest {

    @Mock
    private PlatformSettingsService settings;

    private PublicFiguresService figures;

    @BeforeEach
    void setUp() {
        figures = new PublicFiguresService(settings);
        when(settings.getInt("commission_rate_bps")).thenReturn(1500);
        when(settings.getInt("payment_hold_minutes")).thenReturn(20);
        when(settings.getInt("student_cancel_hours")).thenReturn(12);
        when(settings.getInt("professor_cancel_hours")).thenReturn(12);
        when(settings.getInt("booking_min_lead_hours")).thenReturn(6);
        when(settings.getInt("no_show_report_minutes")).thenReturn(15);
        when(settings.getInt("dispute_report_window_hours")).thenReturn(24);
        when(settings.getInt("auto_complete_hours")).thenReturn(24);
        when(settings.getInt("application_review_business_days")).thenReturn(3);
        when(settings.getInt("assessment_max_minutes")).thenReturn(2);
    }

    @Test
    void laComisionSeEntregaEnPorcentajeNoEnPuntosBasicos() {
        assertThat(figures.figures().commissionPercent()).isEqualTo(15);
    }

    @Test
    void laDuracionDeLaClaseSonLos55QueDiceElDominio() {
        // No es un ajuste: cambiarla es cambiar la aritmética de los cupos ya publicados.
        assertThat(figures.figures().classMinutes()).isEqualTo(55);
    }

    @Test
    void cambiarElAjusteCambiaElNumeroQueSeMuestra() {
        when(settings.getInt("commission_rate_bps")).thenReturn(1800);
        assertThat(figures.figures().commissionPercent()).isEqualTo(18);
        assertThat(figures.marcadores()).containsEntry("comision", "18 %");
    }

    @Test
    void losMarcadoresLlevanElPluralResuelto() {
        Map<String, String> m = figures.marcadores();
        assertThat(m).containsEntry("cancelacion_estudiante", "12 horas")
                .containsEntry("duracion_clase", "55 minutos")
                .containsEntry("revision_postulacion", "3 días hábiles");

        when(settings.getInt("student_cancel_hours")).thenReturn(1);
        when(settings.getInt("application_review_business_days")).thenReturn(1);
        assertThat(figures.marcadores()).containsEntry("cancelacion_estudiante", "1 hora")
                .containsEntry("revision_postulacion", "1 día hábil");
    }
}
