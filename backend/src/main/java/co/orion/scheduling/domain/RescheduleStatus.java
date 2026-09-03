package co.orion.scheduling.domain;

/** Ciclo de una propuesta de reprogramación. Solo PENDING espera respuesta. */
public enum RescheduleStatus {

    PENDING,
    ACCEPTED,
    DECLINED,
    /** Llegó la hora de la clase original y nadie respondió: la reserva siguió su curso. */
    EXPIRED
}
