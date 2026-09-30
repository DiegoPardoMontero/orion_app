package co.orion.identity.application;

import java.util.UUID;

/**
 * Si alguien ya dijo a dónde pagarle: su llave Bre-B.
 *
 * <p>Desde el 29/09/2026 la llave se pide en la postulación (Pardo: «me parece que es sumamente
 * importante y no tiene mucho sentido darles la bienvenida y seguir pidiendo cosas»), así que
 * {@code identity} necesita saberlo para decir qué le falta al aspirante. Los datos viven en
 * {@code billing}, que ya depende de {@code identity}: igual que {@link ProfessorAvailabilityLookup},
 * {@code identity} declara la pregunta y {@code billing} la contesta, y la dependencia sigue yendo en
 * una sola dirección.
 */
public interface PayoutDetailsLookup {

    boolean registered(UUID userId);
}
