package co.orion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.assessment.application.AiUsageRecorder;
import co.orion.assessment.application.AssessmentBudgetService;
import co.orion.assessment.application.ScriptedVoiceProvider;
import co.orion.assessment.application.VoiceSession;
import co.orion.assessment.application.VoiceSessionRequest;
import co.orion.assessment.domain.AiUsageOutcome;
import co.orion.assessment.persistence.AiUsageLogRepository;

/**
 * El freno de gasto del diagnóstico, que es lo que separa una función cara de una función que se
 * come el presupuesto de un mes en una tarde mala.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PresupuestoDelDiagnosticoIT {

    @Autowired private AssessmentBudgetService presupuesto;
    @Autowired private AiUsageRecorder registro;
    @Autowired private AiUsageLogRepository logs;
    @Autowired private ScriptedVoiceProvider voz;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private co.orion.identity.persistence.UserRepository users;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder claves;

    /** actor_id tiene FK a users: un id inventado no es un actor, es un error de integridad. */
    private UUID actor;

    @BeforeEach
    void limpiar() {
        logs.deleteAll();
        actor = users.findByEmailIgnoreCase("diagnostico@orion.test")
                .orElseGet(() -> users.save(new co.orion.identity.domain.User(
                        "diagnostico@orion.test", claves.encode("orion123*"), "Ana Diagnóstico",
                        co.orion.identity.domain.UserRole.STUDENT)))
                .getId();
        tope(80_000);
        jdbc.update("update platform_settings set value = 'true' where key = 'assessment_enabled'");
    }

    @Test
    void sinGastoLaFuncionEstaDisponible() {
        assertThat(presupuesto.gastadoHoy()).isZero();
        assertThat(presupuesto.disponible()).isTrue();
    }

    /** Siete minutos del modelo mini: unos 350 pesos. Es la unidad con la que se llena el tope. */
    @Test
    void unaConversacionCuestaLoQueDura() {
        registro.conversacion(actor, "openai-realtime", "gpt-realtime-2.1-mini",
                Duration.ofMinutes(7), AiUsageOutcome.OK);

        assertThat(presupuesto.gastadoHoy()).isBetween(300L, 400L);
    }

    @Test
    void alLlegarAlTopeLaFuncionSeApagaSola() {
        // Doscientas cincuenta conversaciones de siete minutos pasan de los 80.000 del tope.
        for (int i = 0; i < 250; i++) {
            registro.conversacion(actor, "openai-realtime", "gpt-realtime-2.1-mini",
                    Duration.ofMinutes(7), AiUsageOutcome.OK);
        }

        assertThat(presupuesto.gastadoHoy()).isGreaterThanOrEqualTo(80_000L);
        assertThat(presupuesto.disponible()).isFalse();
    }

    @Test
    void elAvisoSaltaAntesQueElTope() {
        tope(1_000);
        // 800 pesos de gasto: el 80 % de mil, que es donde avisa.
        for (int i = 0; i < 3; i++) {
            registro.conversacion(actor, "openai-realtime", "gpt-realtime-2.1-mini",
                    Duration.ofMinutes(6), AiUsageOutcome.OK);
        }

        assertThat(presupuesto.cercaDelTope()).isTrue();
        // Avisar no es apagar: con el tope sin cruzar, la función sigue en pie.
        assertThat(presupuesto.disponible()).isTrue();
    }

    /** Apagarlo a mano manda por encima del presupuesto: es el interruptor, no una sugerencia. */
    @Test
    void elInterruptorManda() {
        jdbc.update("update platform_settings set value = 'false' where key = 'assessment_enabled'");

        assertThat(presupuesto.disponible()).isFalse();
    }

    /**
     * Un intento que ni siquiera llegó a conversación también deja fila. Un registro que solo anota
     * los éxitos miente sobre el gasto y sobre la fiabilidad a la vez.
     */
    @Test
    void elIntentoFallidoQuedaRegistradoSinCosto() {
        registro.intentoFallido(actor, "openai-realtime", "gpt-realtime-2.1-mini",
                Duration.ofMillis(1_200), AiUsageOutcome.ERROR);

        assertThat(logs.findAll()).singleElement().satisfies(fila -> {
            assertThat(fila.getOutcome()).isEqualTo(AiUsageOutcome.ERROR);
            assertThat(fila.getCostCop()).isZero();
            assertThat(fila.getOccurredAt()).isNotNull();
        });
        assertThat(presupuesto.gastadoHoy()).isZero();
    }

    /** El doble de pruebas no llama a nadie y devuelve credencial de corta vida. */
    @Test
    void elProveedorSembradoNoSaleARed() {
        VoiceSession sesion = voz.start(
                new VoiceSessionRequest("EN", "guion de prueba", 420, "Ana"));

        assertThat(sesion.clientSecret()).startsWith("ek_scripted_");
        assertThat(sesion.expiresAt()).isNotNull();
        assertThat(voz.name()).isEqualTo("scripted");
    }

    private void tope(int pesos) {
        jdbc.update("update platform_settings set value = ? where key = 'assessment_daily_budget_cop'",
                String.valueOf(pesos));
    }
}
