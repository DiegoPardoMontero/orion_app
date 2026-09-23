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
 */
public final class IdiomaDeLaFrase {

    private static final Set<String> PALABRAS_ESPANOLAS = Set.of(
            "que", "de", "la", "los", "las", "el", "es", "con", "para", "por", "una", "del", "al",
            "cuéntame", "cuentas", "sigamos", "español", "cómo", "qué", "estás", "tu", "muy", "pero");

    private IdiomaDeLaFrase() {
    }

    public static boolean pareceEspanol(String frase) {
        if (frase == null || frase.isBlank()) {
            return false;
        }
        String sinNombres = frase.replaceAll("(?i)ori[oó]n|meissa", " ").toLowerCase(Locale.ROOT);
        if (sinNombres.matches("(?s).*[¿¡ñáéíóú].*")) {
            return true;
        }
        long espanolas = Arrays.stream(sinNombres.split("[^\\p{L}]+"))
                .filter(PALABRAS_ESPANOLAS::contains).distinct().count();
        return espanolas >= 3;
    }
}
