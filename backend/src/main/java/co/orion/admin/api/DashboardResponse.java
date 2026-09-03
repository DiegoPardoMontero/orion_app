package co.orion.admin.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * El pulso de Orión en una pantalla. Todo son cifras reales consultadas en el momento: no hay
 * ningún número decorativo aquí.
 */
public record DashboardResponse(People people,
                                Lessons lessons,
                                Money money,
                                Attention attention,
                                List<JobHealth> jobs) {

    public record People(long students, long professors, long admins,
                         long professorsPublished, long applicationsPending) {
    }

    /** Clases por estado, más las dos cifras de actividad reciente. */
    public record Lessons(Map<String, Long> byStatus,
                          long bookedLast7Days,
                          double selfServicePercentage) {
    }

    /**
     * El dinero, en los tres estados que importan. {@code heldCop} es lo que Orión debe y todavía
     * no ha liberado; {@code payableCop}, lo que ya se ganaron los profesores y falta transferir.
     */
    public record Money(long heldCop, long payableCop, long transferredCop,
                        long commissionEarnedCop, long outstandingCreditCop) {
    }

    /** Lo que espera una decisión humana. Si todo está en cero, no hay nada que hacer hoy. */
    public record Attention(long openDisputes, long paymentsNeedingReview,
                            long proposedSanctions, long pendingReschedules,
                            long reportedReviews) {
    }

    /**
     * El estado de cada job. El de autocompletado es el que le paga a los profesores: si deja de
     * correr, el síntoma llega semanas después como "no me han pagado".
     */
    public record JobHealth(String job, ZonedDateTime lastRunAt, boolean ok, String detail) {
    }
}
