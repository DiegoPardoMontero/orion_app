package co.orion.identity.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * La habilitación de un profe para dar clase a adolescentes (Bloque 12): si está vigente y hasta
 * cuándo, y qué le falta para que el admin la encienda.
 */
public record MinorsClearanceView(boolean active,
                                  LocalDate clearedUntil,
                                  Instant clearedAt,
                                  boolean conductAccepted,
                                  boolean registryCheck,
                                  boolean backgroundCheck,
                                  /** Lo que falta, en palabras para la pantalla; vacío si ya puede habilitarse. */
                                  List<String> missing,
                                  /** Los certificados subidos (consulta del Registro y antecedentes), para verlos y quitarlos. */
                                  List<DocumentView> documents) {

    /** Lo único que bloquea al admin es el código de conducta; los certificados se ven, no bloquean (15/10/2026). */
    public boolean readyToClear() {
        return conductAccepted;
    }
}
