package co.orion.scheduling.domain;

/**
 * Por qué el estudiante se quedó sin su clase por culpa del profesor.
 *
 * <p>Las dos pesan igual en la escalera de sanciones —el estudiante se quedó sin clase el mismo
 * día en los dos casos— pero no son la misma falta, y quien las revisa tiene que poder
 * distinguirlas: quien avisó con dos horas hizo algo peor que nada y mejor que desaparecer.
 */
public enum AbsenceKind {

    /** No se presentó, y un reclamo del estudiante lo confirmó. */
    NO_SHOW,

    /** Canceló dentro de la ventana en la que ya no se devuelve el dinero. */
    LATE_CANCELLATION
}
