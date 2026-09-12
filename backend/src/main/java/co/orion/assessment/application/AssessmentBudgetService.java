package co.orion.assessment.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.assessment.persistence.AiUsageLogRepository;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.time.BusinessZone;

/**
 * El freno de gasto del diagnóstico.
 *
 * <p>Al llegar al tope, la función <strong>se apaga sola</strong> y la portada vuelve a mostrar el
 * buscador de siempre, sin mensaje de error ni botón deshabilitado: quien llega no tiene por qué
 * enterarse de un problema de presupuesto nuestro.
 *
 * <p>El día es el de Bogotá y no el del servidor: un tope diario que se reinicia a las siete de la
 * tarde hora local no es un tope diario.
 */
@Service
public class AssessmentBudgetService {

    private static final String ENABLED = "assessment_enabled";
    private static final String DAILY_BUDGET = "assessment_daily_budget_cop";

    /** A partir de aquí se avisa al administrador. El tope sorprende menos si se ve venir. */
    public static final int PORCENTAJE_DE_AVISO = 80;

    private final AiUsageLogRepository logs;
    private final PlatformSettingsService settings;
    private final Clock clock;

    public AssessmentBudgetService(AiUsageLogRepository logs,
                                   PlatformSettingsService settings,
                                   Clock clock) {
        this.logs = logs;
        this.settings = settings;
        this.clock = clock;
    }

    /** ¿Se puede ofrecer el diagnóstico ahora mismo? Interruptor y presupuesto, en ese orden. */
    @Transactional(readOnly = true)
    public boolean disponible() {
        return settings.getBoolean(ENABLED) && gastadoHoy() < settings.getInt(DAILY_BUDGET);
    }

    @Transactional(readOnly = true)
    public long gastadoHoy() {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
        Instant desde = hoy.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant();
        return logs.spentBetween(AiUsageRecorder.FEATURE_DIAGNOSTICO, desde, hasta);
    }

    /** Si el gasto de hoy cruzó el umbral de aviso. Lo consulta el vigilante, no el camino de uso. */
    @Transactional(readOnly = true)
    public boolean cercaDelTope() {
        long tope = settings.getInt(DAILY_BUDGET);
        return tope > 0 && gastadoHoy() * 100 >= tope * PORCENTAJE_DE_AVISO;
    }
}
