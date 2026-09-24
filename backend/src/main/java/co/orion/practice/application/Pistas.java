package co.orion.practice.application;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.practice.domain.PracticeItemType;

/**
 * La pista del «Casi…» nunca regala la respuesta. Contra OpenAI salieron pistas que eran la respuesta
 * misma («luggage», «have → am»); una así se cambia por la pista genérica de su tipo, que ayuda a
 * pensar sin decir nada. Lo mismo si no llegó ninguna.
 */
final class Pistas {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<PracticeItemType, String> GENERICAS = Map.of(
            PracticeItemType.FILL_BLANK, "Relee la frase completa: ¿qué palabra de tu clase encaja ahí?",
            PracticeItemType.MATCH_MEANING, "Esas dos no van juntas. Piensa en cómo las usaron en tu clase.",
            PracticeItemType.FIX_SENTENCE, "Compárala con lo que viste en clase y cambia lo que haga falta.",
            PracticeItemType.SPOT_ERROR, "Léela despacio, palabra por palabra: ¿cuál no suena bien?",
            PracticeItemType.BUILD_SENTENCE, "Piensa en el orden: quién hace la acción y qué viene después.",
            PracticeItemType.ORDER_DIALOGUE, "Busca quién abre la conversación y qué responde a cada pregunta.",
            PracticeItemType.CHOOSE_REPLY, "Lee otra vez el mensaje: ¿qué te están preguntando?",
            PracticeItemType.LISTEN_CHOOSE, "Escúchala otra vez, más despacio.",
            PracticeItemType.DICTATION, "Escúchala otra vez, más despacio, palabra por palabra.",
            PracticeItemType.WRITE_SENTENCE, "Escríbela toda en inglés, con la palabra de tu clase adentro.");

    private Pistas() {
    }

    static String segura(PracticeGenerator.Generado g) {
        String pista = g.pista() == null ? "" : g.pista().strip();
        if (pista.split("\\s+").length < 4 || regalaLaRespuesta(g, pista)) {
            return GENERICAS.get(g.tipo());
        }
        return pista;
    }

    /** ¿Dice la respuesta? Lo esperado —o, en cazar el error, la corrección— dentro de la pista. */
    static boolean regalaLaRespuesta(PracticeGenerator.Generado g, String pista) {
        if (pista.contains("→") || pista.contains("->")) {
            return true;
        }
        String p = norma(pista);
        String esperada = g.expected();
        if (esperada == null) {
            return false;
        }
        try {
            if (g.tipo() == PracticeItemType.SPOT_ERROR) {
                JsonNode e = JSON.readTree(esperada);
                return p.contains(norma(e.path("correction").asText("")));
            }
            if (g.tipo() == PracticeItemType.MATCH_MEANING) {
                JsonNode e = JSON.readTree(esperada);
                for (JsonNode significado : e) {
                    if (p.contains(norma(significado.asText()))) {
                        return true;
                    }
                }
                return false;
            }
            if (g.tipo() == PracticeItemType.ORDER_DIALOGUE || g.tipo() == PracticeItemType.BUILD_SENTENCE) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
        String e = norma(esperada);
        return !e.isEmpty() && p.contains(e);
    }

    private static String norma(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}¿¡«»“”]", " ").replaceAll("\\s+", " ").strip();
    }
}
