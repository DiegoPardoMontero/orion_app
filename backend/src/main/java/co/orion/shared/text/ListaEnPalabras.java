package co.orion.shared.text;

import java.util.List;

/**
 * Una lista dicha como se dice en español: «tu foto, tu nivel y el idioma», con la última unión en
 * «y» y no en coma, y el verbo en plural cuando son varias cosas —«te faltan tus horarios»—. Los
 * recordatorios de la ficha y del perfil la armaban con {@code String.join(", ")} y un «te falta» fijo.
 */
public final class ListaEnPalabras {

    private ListaEnPalabras() {
    }

    /** «tu foto», «tu foto y tu tarifa», «tu foto, tu tarifa y tus horarios». */
    public static String unir(List<String> cosas) {
        if (cosas.isEmpty()) {
            return "";
        }
        if (cosas.size() == 1) {
            return cosas.getFirst();
        }
        return String.join(", ", cosas.subList(0, cosas.size() - 1)) + " y " + cosas.getLast();
    }

    /**
     * «falta» o «faltan». Plural si son varias cosas o si la única ya es plural («tus horarios», «los
     * idiomas que enseñas»): la lista es de un vocabulario cerrado que empieza siempre por su artículo o
     * su posesivo, así que basta con mirar esa primera palabra.
     */
    public static String falta(List<String> cosas) {
        return esPlural(cosas) ? "faltan" : "falta";
    }

    /** «te falta tu foto», «te faltan tu foto y tu tarifa». En minúscula, para ir a mitad de frase. */
    public static String teFalta(List<String> cosas) {
        return "te " + falta(cosas) + " " + unir(cosas);
    }

    /** Lo mismo al comienzo de una frase: «Te falta tu foto». */
    public static String teFaltaAlComienzo(List<String> cosas) {
        return "T" + teFalta(cosas).substring(1);
    }

    static boolean esPlural(List<String> cosas) {
        if (cosas.size() > 1) {
            return true;
        }
        if (cosas.isEmpty()) {
            return false;
        }
        String primera = cosas.getFirst().trim().toLowerCase();
        return primera.startsWith("los ") || primera.startsWith("las ") || primera.startsWith("tus ")
                || primera.startsWith("sus ");
    }
}
