package co.orion.scheduling.application;

import java.util.UUID;

/**
 * Quién está dentro de una sala ahora mismo.
 *
 * <p>La antesala lo necesita para decir «María te espera» en vez de «aún no ha entrado», y la
 * pantalla de conexión caída, para distinguir «se le cayó a la otra persona» de «se acabó».
 *
 * <p>Detrás de interfaz porque la respuesta buena viene de los <em>webhooks</em> de JaaS
 * ({@code PARTICIPANT_JOINED} / {@code PARTICIPANT_LEFT}), que son un hecho del servidor de 8x8 y
 * no una declaración del navegador. Mientras esos webhooks no estén configurados, la implementación
 * por defecto contesta que no hay nadie: es la única respuesta honesta, y la pantalla ya tiene un
 * estado para ella.
 */
public interface RoomPresence {

    boolean estaDentro(UUID bookingId, UUID userId);
}
