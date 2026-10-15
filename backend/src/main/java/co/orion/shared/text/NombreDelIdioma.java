package co.orion.shared.text;

import java.util.Map;

/**
 * El nombre de un idioma enseñado, a partir de su código del catálogo ({@code languages.code}).
 *
 * <p>Vive en {@code shared} porque lo necesitan tres módulos que no se conocen: el diagnóstico (el
 * guion de Meissa), el acta (el borrador por IA) y la práctica. Antes cada uno tenía su propio
 * «si es EN, inglés»; con el francés en el catálogo (V82) eso dejaba «FR» crudo en un prompt.
 */
public final class NombreDelIdioma {

    private static final Map<String, String> EN_ESPANOL = Map.of(
            "EN", "inglés",
            "FR", "francés",
            "ES", "español");

    /**
     * El mismo idioma, dicho en inglés: un modelo de voz tiende a contestar en el idioma de sus
     * instrucciones, y la regla del idioma se le da en el idioma que tiene que hablar.
     */
    private static final Map<String, String> IN_ENGLISH = Map.of(
            "EN", "English",
            "FR", "French",
            "ES", "Spanish");

    private NombreDelIdioma() {
    }

    /** Sin código, inglés: es el idioma de siempre y el de las clases anteriores a la V20. */
    public static String codigo(String languageCode) {
        return languageCode == null || languageCode.isBlank() ? "EN" : languageCode.toUpperCase();
    }

    public static String enEspanol(String languageCode) {
        return EN_ESPANOL.getOrDefault(codigo(languageCode), "inglés");
    }

    public static String inEnglish(String languageCode) {
        return IN_ENGLISH.getOrDefault(codigo(languageCode), "English");
    }
}
