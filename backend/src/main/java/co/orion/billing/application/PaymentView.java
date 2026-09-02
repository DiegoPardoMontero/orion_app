package co.orion.billing.application;

import co.orion.billing.domain.Payment;
import co.orion.scheduling.domain.Booking;

/**
 * Un pago con el contexto que hace falta para mostrarlo: la clase y los nombres de las dos partes.
 * Se arma en el servicio para que los DTO de la API no tengan que consultar nada.
 */
public record PaymentView(Payment payment,
                          Booking booking,
                          String studentName,
                          String professorName) {
}
