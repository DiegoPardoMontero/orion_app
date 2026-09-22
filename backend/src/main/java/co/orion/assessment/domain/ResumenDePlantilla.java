package co.orion.assessment.domain;

import java.util.List;
import java.util.Locale;

/**
 * El resumen cuando la IA no lo escribe: sin proveedor, con el proveedor caído o con una salida que
 * no pasó la revisión. Nunca dice nada que no sea cierto de cualquiera que terminó la conversación,
 * y nombra sus objetivos solo si los marcó.
 */
public final class ResumenDePlantilla {

    private ResumenDePlantilla() {
    }

    public static String para(String nombreDePila, List<String> objetivos) {
        String saludo = nombreDePila == null || nombreDePila.isBlank()
                ? "Gracias por conversar con Meissa."
                : "Gracias por conversar con Meissa, " + nombreDePila.trim() + ".";

        if (objetivos == null || objetivos.isEmpty()) {
            return saludo + " Con lo que nos contaste, vemos muchas posibilidades de que Orión te"
                    + " ayude a hablar inglés con más confianza.";
        }
        return saludo + " Con lo que nos contaste, vemos muchas posibilidades de que Orión te"
                + " ayude a avanzar en lo que buscas: " + enumerar(objetivos) + ".";
    }

    /** «trabajo», «trabajo y viajes», «trabajo, viajes y estudio». */
    private static String enumerar(List<String> objetivos) {
        List<String> nombres = objetivos.stream()
                .map(o -> o.trim().toLowerCase(Locale.forLanguageTag("es-CO")))
                .toList();
        if (nombres.size() == 1) {
            return nombres.getFirst();
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1))
                + " y " + nombres.getLast();
    }
}
