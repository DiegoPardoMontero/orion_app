package co.orion.support.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.support.domain.SupportTicket;

/** Una fila de la lista. Lleva el vencimiento porque es lo que ordena la bandeja del admin. */
public record TicketSummary(String code, String category, String categoryLabel, String subject,
                            String status, UUID bookingId, Instant dueAt, boolean overdue,
                            Instant createdAt, Instant updatedAt) {

    public static TicketSummary from(SupportTicket t, Instant now) {
        return new TicketSummary(
                t.getCode(),
                t.getCategory().name(),
                t.getCategory().getEtiqueta(),
                t.getSubject(),
                t.getStatus().name(),
                t.getBookingId(),
                t.getDueAt(),
                t.isOverdue(now),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
