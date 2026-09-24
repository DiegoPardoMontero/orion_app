package co.orion.practice.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import co.orion.practice.domain.PracticeItemType;

/**
 * La práctica sin IA, para local y pruebas: determinista y solo con lo que el acta dice literal.
 * Por cada término del vocabulario, un hueco en la frase del acta que lo contiene y una frase
 * propia; si hay significados, emparejarlos; y oír el primero y escribirlo. Sin corregir frases ni
 * ordenar diálogos: eso exige inventar, y sin IA no se inventa.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "scripted",
        matchIfMissing = true)
public class GeneradorSinIa implements PracticeGenerator {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public List<Generado> generar(UUID estudianteId, Material material, int cuantos) {
        List<Material.Termino> terminos = material.vocabulary().stream()
                .filter(t -> t.term() != null && !t.term().isBlank()).toList();
        List<Generado> huecos = new ArrayList<>();
        List<Generado> frases = new ArrayList<>();
        for (Material.Termino t : terminos) {
            fraseQueLoContiene(material, t.term()).ifPresent(frase -> huecos.add(hueco(frase, t.term(), terminos)));
            frases.add(new Generado(PracticeItemType.WRITE_SENTENCE,
                    "Escribe una frase tuya usando «" + t.term() + "».",
                    JSON.createObjectNode().put("term", t.term()).toString(), null,
                    "Cualquier frase tuya que use «" + t.term() + "» vale: lo que importa es usarla.", t.term(),
                    "Escríbela toda en inglés, con «" + t.term() + "» adentro."));
        }
        // Escuchar el primer término: lo único de escucha que se puede anclar sin inventar nada.
        List<Generado> escucha = terminos.stream().limit(1).map(GeneradorSinIa::dictado).toList();
        List<Material.Termino> conSignificado = terminos.stream()
                .filter(t -> t.meaning() != null && !t.meaning().isBlank()).limit(4).toList();

        // Tipos variados: se alternan en vez de llenar el set con cuatro iguales.
        List<Generado> salida = new ArrayList<>();
        if (conSignificado.size() >= 2) {
            salida.add(emparejar(conSignificado));
        }
        salida.addAll(escucha);
        // La frase propia, desde el último término: con uno de cada tipo, si no, el hueco, la escucha
        // y la frase propia caerían los tres sobre la misma palabra.
        Collections.reverse(frases);
        int i = 0;
        while (salida.size() < cuantos && (i < huecos.size() || i < frases.size())) {
            if (i < huecos.size() && salida.size() < cuantos) {
                salida.add(huecos.get(i));
            }
            if (i < frases.size() && salida.size() < cuantos) {
                salida.add(frases.get(i));
            }
            i++;
        }
        return salida;
    }

    private static java.util.Optional<String> fraseQueLoContiene(Material m, String termino) {
        String todo = (m.workedOn() == null ? "" : m.workedOn()) + "\n" + (m.recurringIssues() == null ? "" : m.recurringIssues());
        for (String frase : todo.split("(?<=[.!?;])\\s+|\\n")) {
            if (frase.toLowerCase(Locale.ROOT).contains(termino.toLowerCase(Locale.ROOT))) {
                return java.util.Optional.of(frase.strip());
            }
        }
        return java.util.Optional.empty();
    }

    private static Generado hueco(String frase, String termino, List<Material.Termino> todos) {
        int desde = frase.toLowerCase(Locale.ROOT).indexOf(termino.toLowerCase(Locale.ROOT));
        String conHueco = frase.substring(0, desde) + "___" + frase.substring(desde + termino.length());
        ObjectNode payload = JSON.createObjectNode().put("sentence", conHueco);
        ArrayNode opciones = payload.putArray("options");
        todos.stream().map(Material.Termino::term).distinct().limit(3).forEach(opciones::add);
        if (todos.stream().map(Material.Termino::term).distinct().limit(3).noneMatch(termino::equals)) {
            opciones.add(termino);
        }
        return new Generado(PracticeItemType.FILL_BLANK, "Completa la frase de tu clase.", payload.toString(),
                termino, "Aquí va «" + termino + "», como lo trabajaron en clase.", termino,
                "Es una de las palabras nuevas de tu clase: búscala en tu resumen.");
    }

    private static Generado dictado(Material.Termino t) {
        return new Generado(PracticeItemType.DICTATION, "Escucha y escribe lo que dice Meissa.",
                JSON.createObjectNode().put("say", t.term()).toString(), t.term(),
                "Es «" + t.term() + "», una de las expresiones de tu clase.", t.term(),
                "Escúchala otra vez, más despacio.");
    }

    private static Generado emparejar(List<Material.Termino> pares) {
        ObjectNode payload = JSON.createObjectNode();
        ArrayNode terminos = payload.putArray("terms");
        ArrayNode significados = payload.putArray("meanings");
        ObjectNode esperado = JSON.createObjectNode();
        pares.forEach(p -> {
            terminos.add(p.term());
            esperado.put(p.term(), p.meaning());
        });
        // Al revés, para que no queden alineados con su término.
        for (int i = pares.size() - 1; i >= 0; i--) {
            significados.add(pares.get(i).meaning());
        }
        return new Generado(PracticeItemType.MATCH_MEANING, "Une cada palabra con su significado.",
                payload.toString(), esperado.toString(), "Son las palabras nuevas de tu última clase.", null);
    }
}
