package co.orion.assessment.application;

import java.util.Optional;
import java.util.UUID;

/**
 * La traducción al español de lo que dice Meissa, frase por frase y mientras habla (handoff de
 * Meissa: «pregunta en inglés; traducción al español debajo, ocultable»).
 *
 * <p>Vacío cuando no hay traducción que mostrar: el proveedor falló o tardó, o la frase ya estaba
 * en español —la rama en español no se traduce a sí misma—. Nunca lanza: una frase sin traducir
 * es una molestia, una conversación caída por la traducción sería un fallo.
 */
public interface TraductorDeFrases {

    /** @param actorId a quién se carga el gasto; nulo para un lead */
    Optional<String> alEspanol(UUID actorId, String frase);
}
