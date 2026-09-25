package co.orion.catalog.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.api.PublicFigures;
import co.orion.shared.time.ClassLength;

/**
 * La única fuente de los números que aparecen escritos, para pantallas y para documentos legales.
 *
 * <p>Regla de Pardo (15/09/2026): lo que se cambia desde Ajustes cambia en todas partes. Este
 * servicio es lo que la hace cierta — quien quiera escribir «12 horas» en una pantalla o en una
 * cláusula, lo pide aquí en vez de teclearlo.
 *
 * <p>No cachea, igual que {@link PlatformSettingsService}: un ajuste que se cambia en la pantalla de
 * Ajustes tiene que verse en la siguiente petición, no en el siguiente despliegue.
 */
@Service
public class PublicFiguresService {

    private final PlatformSettingsService settings;

    public PublicFiguresService(PlatformSettingsService settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public PublicFigures figures() {
        return new PublicFigures(
                settings.getInt("commission_rate_bps") / 100,
                ClassLength.MINUTES,
                settings.getInt("payment_hold_minutes"),
                settings.getInt("student_cancel_hours"),
                settings.getInt("professor_cancel_hours"),
                settings.getInt("booking_min_lead_hours"),
                settings.getInt("no_show_report_minutes"),
                settings.getInt("dispute_report_window_hours"),
                settings.getInt("auto_complete_hours"),
                settings.getInt("application_review_business_days"),
                settings.getInt("assessment_max_minutes"),
                settings.getInt("assessment_lead_retention_days"),
                settings.getInt("founder_commission_rate_bps") / 100,
                settings.getInt("founder_period_months"));
    }

    /**
     * Los mismos números, ya escritos en español, para rellenar los {@code {{marcadores}}} de los
     * documentos legales.
     *
     * <p>Aquí sí se entregan como frase y no como número: una cláusula dice «doce (12) horas», no
     * «12». Y por eso el plural va resuelto — «1 hora» y «12 horas» no se arman concatenando.
     */
    @Transactional(readOnly = true)
    public Map<String, String> marcadores() {
        PublicFigures f = figures();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comision", f.commissionPercent() + " %");
        m.put("duracion_clase", minutos(f.classMinutes()));
        m.put("retencion_pago", minutos(f.paymentHoldMinutes()));
        m.put("cancelacion_estudiante", horas(f.studentCancelHours()));
        m.put("cancelacion_profesor", horas(f.professorCancelHours()));
        m.put("antelacion_minima", horas(f.bookingMinLeadHours()));
        m.put("reporte_ausencia", minutos(f.noShowReportMinutes()));
        m.put("ventana_reclamo", horas(f.disputeReportWindowHours()));
        m.put("cierre_automatico", horas(f.autoCompleteHours()));
        m.put("revision_postulacion", diasHabiles(f.applicationReviewBusinessDays()));
        m.put("duracion_diagnostico", minutos(f.assessmentMinutes()));
        return m;
    }

    private static String minutos(int n) {
        return n + (n == 1 ? " minuto" : " minutos");
    }

    private static String horas(int n) {
        return n + (n == 1 ? " hora" : " horas");
    }

    private static String diasHabiles(int n) {
        return n + (n == 1 ? " día hábil" : " días hábiles");
    }
}
