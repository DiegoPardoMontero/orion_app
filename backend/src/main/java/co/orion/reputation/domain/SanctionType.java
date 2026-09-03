package co.orion.reputation.domain;

import java.time.Duration;

/**
 * La escalera de sanciones, de menos a más. Progresiva a propósito: la primera falta de alguien que
 * lleva un año cumpliendo no es lo mismo que la cuarta en tres meses.
 */
public enum SanctionType {

    /** Aviso. No toca la visibilidad: solo deja constancia y le avisa al profesor. */
    WARNING(null),

    /** Baja en el ranking durante dos semanas: se le recomienda menos, no se le esconde. */
    VISIBILITY_REDUCED(Duration.ofDays(14)),

    /** No recibe reservas NUEVAS durante una semana. Las ya confirmadas se respetan siempre. */
    BOOKINGS_SUSPENDED(Duration.ofDays(7)),

    /** Fuera del marketplace hasta que una persona revise el caso. */
    PROFILE_HIDDEN(null),

    /** Cierre de cuenta. SIEMPRE manual: ningún automatismo puede quitarle a alguien su sustento. */
    ACCOUNT_SUSPENDED(null);

    private final Duration duration;

    SanctionType(Duration duration) {
        this.duration = duration;
    }

    /** Null = no caduca sola; la levanta una persona. */
    public Duration duration() {
        return duration;
    }

    /** ¿Impide que le entren reservas nuevas? */
    public boolean blocksNewBookings() {
        return this == BOOKINGS_SUSPENDED || this == PROFILE_HIDDEN || this == ACCOUNT_SUSPENDED;
    }

    /** ¿Lo saca del buscador? */
    public boolean hidesFromMarketplace() {
        return this == PROFILE_HIDDEN || this == ACCOUNT_SUSPENDED;
    }

    /**
     * Qué corresponde según cuántas ausencias confirmadas acumula en la ventana. La cuenta empieza
     * en 1: la primera ausencia es un aviso, no un castigo.
     */
    public static SanctionType forAbsenceCount(long absences) {
        if (absences <= 1) {
            return WARNING;
        }
        if (absences == 2) {
            return VISIBILITY_REDUCED;
        }
        if (absences == 3) {
            return BOOKINGS_SUSPENDED;
        }
        return PROFILE_HIDDEN;
    }
}
