package co.orion.lifecycle.domain;

/** Los recordatorios de una clase. El CHECK de la V60 lista los mismos. */
public enum ClassReminderKind {
    /** Menos de un día antes: campana y correo a los dos. */
    DAY_BEFORE,
    /** Una hora antes: campana (y notificación del dispositivo) a los dos. */
    HOUR_BEFORE,
    /** Un día después de una clase dictada que el estudiante no calificó: solo a él. */
    RATE
}
