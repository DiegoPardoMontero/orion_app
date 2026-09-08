package co.orion.support.domain;

/** En qué punto está una solicitud. */
public enum TicketStatus {

    /** Esperando a que Orión conteste. Es lo que cuenta contra el plazo legal. */
    OPEN,

    /** Contestada. Sigue abierta para quien la escribió: puede responder y vuelve a OPEN. */
    ANSWERED,

    /** Cerrada. Solo la cierra el administrador, y quien la abrió puede reabrirla escribiendo. */
    CLOSED
}
