package co.orion.assessment.domain;

import java.util.List;
import java.util.Map;

/**
 * El resultado del cálculo.
 *
 * @param valor       0–100
 * @param version     la fórmula con la que se calculó, huella de los pesos incluida
 * @param dimensiones cada dimensión normalizada a 0–100, para poder explicar el número
 * @param observadas  qué se notó, en códigos
 */
public record Puntaje(int valor,
                      String version,
                      Map<String, Integer> dimensiones,
                      List<Observacion> observadas) {
}
