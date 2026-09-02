package co.orion.scheduling.application;

import java.time.Instant;

import co.orion.scheduling.domain.Booking;

/**
 * El cobro, visto desde scheduling. Mismo patrón que {@code MeetingLinkProvider}: el puerto lo
 * declara quien lo necesita y lo implementa el módulo {@code billing}, de modo que scheduling no
 * sabe qué es una comisión ni cómo se llama la pasarela.
 *
 * La dirección de la dependencia importa: scheduling → (puerto) ← billing. En sentido contrario,
 * billing sí llama a scheduling directamente (para confirmar o vencer una reserva) y reacciona a
 * sus eventos. No hay ciclo: billing conoce a scheduling, scheduling solo conoce esta interfaz.
 */
public interface PaymentInitiator {

    /** Hasta cuándo se le guarda el cupo al estudiante mientras paga. */
    Instant holdExpiry(Instant now);

    /**
     * Abre el libro contable de la reserva: fija precio y comisión, gasta el crédito disponible y
     * prepara el cobro de lo que quede. La reserva ya tiene id (el cobro se referencia contra él).
     */
    PaymentTicket initiate(Booking booking);
}
