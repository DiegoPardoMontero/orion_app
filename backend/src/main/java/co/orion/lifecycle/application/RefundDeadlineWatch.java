package co.orion.lifecycle.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.orion.billing.domain.RefundRequest;
import co.orion.billing.persistence.RefundRequestRepository;
import co.orion.shared.observability.AlertService;
import co.orion.shared.time.BusinessZone;

/**
 * Avisa por correo de las devoluciones que se acercan a su plazo legal.
 *
 * <p>Es la pieza que convierte un cumplimiento manual en uno fiable. El dinero lo mueve una persona
 * en el panel de Wompi porque Wompi no lo expone por API; lo que no puede depender de una persona
 * es <em>acordarse</em>. Sin esto, el único aviso de que un plazo del art. 47 venció sería una
 * queja ante la SIC.
 *
 * <p>Un solo correo al día con todas, y no uno por devolución: cinco correos separados sobre el
 * mismo asunto se leen como ruido, y lo que hay que hacer con ellos es lo mismo — sentarse una vez
 * en Wompi y despacharlas.
 */
@Component
public class RefundDeadlineWatch {

    /** A partir de aquí se avisa. Cinco días dan margen de sobra para una transferencia. */
    private static final Duration AVISO_ANTES_DE = Duration.ofDays(5);

    private final RefundRequestRepository refunds;
    private final AlertService alerts;
    private final Clock clock;

    public RefundDeadlineWatch(RefundRequestRepository refunds, AlertService alerts, Clock clock) {
        this.refunds = refunds;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** Una vez al día, a las 8 de la mañana de Bogotá: cuando alguien puede actuar. */
    @Scheduled(cron = "${orion.alerts.refunds.cron:0 0 8 * * *}", zone = "America/Bogota")
    public void revisar() {
        Instant now = clock.instant();
        List<RefundRequest> porVencer = refunds.findByStatusAndDueAtBefore(
                RefundRequest.Status.PENDING, now.plus(AVISO_ANTES_DE));

        if (porVencer.isEmpty()) {
            return;
        }

        long vencidas = porVencer.stream().filter(r -> r.isOverdue(now)).count();
        StringBuilder cuerpo = new StringBuilder(
                "Hay " + porVencer.size() + " devolución(es) por hacer en el panel de Wompi");
        if (vencidas > 0) {
            cuerpo.append(", y ").append(vencidas).append(" ya pasó su plazo legal");
        }
        cuerpo.append(".\n\n");

        for (RefundRequest r : porVencer) {
            cuerpo.append(r.isOverdue(now) ? "  VENCIDA  " : "  pendiente ")
                    .append("$").append(String.format("%,d", r.getAmountCop()).replace(',', '.'))
                    .append(" COP · vence el ")
                    .append(LocalDate.ofInstant(r.getDueAt(), BusinessZone.BOGOTA))
                    .append(" · reserva ").append(r.getBookingId())
                    .append('\n');
        }
        cuerpo.append("\nSe despachan desde Pagos → Devoluciones. Sin la referencia de Wompi no se "
                + "pueden cerrar.");

        // Firma fija por día: el freno de AlertService deja pasar una al día y agrupa el resto,
        // que es justo lo que queremos de un resumen diario.
        alerts.alert("refunds-due:" + LocalDate.ofInstant(now, BusinessZone.BOGOTA),
                vencidas > 0
                        ? "Devoluciones VENCIDAS (" + vencidas + ")"
                        : "Devoluciones por vencer (" + porVencer.size() + ")",
                cuerpo.toString());
    }
}
