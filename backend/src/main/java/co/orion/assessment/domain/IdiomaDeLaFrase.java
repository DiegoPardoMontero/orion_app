package co.orion.assessment.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Si una frase de Meissa ya está en español: la rama en español no se traduce.
 *
 * <p>Se decide aquí y no se le deja al modelo porque el modelo rápido, puesto a «traducir» una
 * frase en español, la reescribe («Sigamos en español…» volvía como «Claro, sigamos en español…»)
 * y debajo aparecía una traducción de algo que ya se leía en español. Una regla simple basta: el
 * inglés de Meissa no lleva tildes, eñes ni signos de apertura, salvo en «Orión», que se quita
 * antes de mirar. Clase pura, sin Spring.
 *
 * <p>Tampoco los nombres propios: «Tell me more, Sofía» o «the traffic in Bogotá» son inglés. Se
 * quitan las palabras con mayúscula en mitad de la frase y la del principio cuando va seguida de coma
 * («Sofía, what do you…»), que es como Meissa llama a la persona. Eso deja sin pistas a las frases
 * cortas en español que empiezan igual («Sí, claro.», «Hola, Sofía.»), y por eso antes se buscan en
 * el texto entero unas pocas palabras que el inglés no usa.
 */
public final class IdiomaDeLaFrase {

    private static final Set<String> PALABRAS_ESPANOLAS = Set.of(
            "que", "de", "la", "los", "las", "el", "es", "con", "para", "por", "una", "del", "al",
            "cuéntame", "cuentas", "sigamos", "español", "cómo", "qué", "estás", "tu", "muy", "pero");

    /** Basta una: son español sin duda y no son nombres. */
    private static final Set<String> INEQUIVOCAS = Set.of(
            "sí", "hola", "gracias", "claro", "bueno", "perfecto", "ajá", "cuéntame", "genial", "vale");

    /** Una palabra con mayúscula detrás de otra palabra o de una coma: un nombre propio. */
    private static final String NOMBRE_EN_MEDIO = "(?<=[\\p{L},;:]\\s)\\p{Lu}\\p{L}*";
    /** La primera palabra, con mayúscula y seguida de coma: la persona a la que se le habla. */
    private static final String NOMBRE_AL_EMPEZAR = "^\\s*\\p{Lu}\\p{L}*(?=,)";

    private IdiomaDeLaFrase() {
    }

    public static boolean pareceEspanol(String frase) {
        if (frase == null || frase.isBlank()) {
            return false;
        }
        if (Arrays.stream(frase.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")).anyMatch(INEQUIVOCAS::contains)) {
            return true;
        }
        String sinNombres = frase.replaceAll("(?i)ori[oó]n|meissa", " ")
                .replaceAll(NOMBRE_EN_MEDIO, " ").replaceAll(NOMBRE_AL_EMPEZAR, " ")
                .toLowerCase(Locale.ROOT);
        if (sinNombres.matches("(?s).*[¿¡ñáéíóú].*")) {
            return true;
        }
        long espanolas = Arrays.stream(sinNombres.split("[^\\p{L}]+"))
                .filter(PALABRAS_ESPANOLAS::contains).distinct().count();
        return espanolas >= 3;
    }
}
