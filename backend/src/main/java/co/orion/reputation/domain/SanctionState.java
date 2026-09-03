package co.orion.reputation.domain;

/**
 * Si la sanción surte efecto o solo está propuesta.
 *
 * PROPOSED existe por una decisión de Pardo (02/09/2026): con pocos profesores, un automatismo que
 * oculta perfiles puede sacar a alguien del marketplace sin que nadie lo mire. En modo observación
 * el sistema calcula lo que correspondería y lo deja propuesto; una persona confirma.
 */
public enum SanctionState {

    PROPOSED,
    ACTIVE,
    REVOKED
}
