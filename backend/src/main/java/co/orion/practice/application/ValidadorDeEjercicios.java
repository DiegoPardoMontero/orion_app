package co.orion.practice.application;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.practice.domain.Evaluador;
import co.orion.practice.domain.PracticeItemType;

/**
 * Las reglas duras de generación (brief, paso B2), aplicadas a lo que devuelva cualquier generador:
 *
 * <ul>
 *   <li><strong>Todo ejercicio se ancla al acta.</strong> Los de vocabulario tienen que usar un
 *       término real del acta; corregir una frase exige que el acta hable de un error recurrente,
 *       y ordenar un diálogo, que diga qué se trabajó. Lo que no se ancla se descarta.</li>
 *   <li>Un ejercicio mal formado se descarta él solo, no el set entero.</li>
 *   <li>Tipos variados: como mucho dos del mismo tipo, nunca cuatro iguales.</li>
 * </ul>
 *
 * <p>Clase pura: el material y la salida entran por parámetro.
 */
public final class ValidadorDeEjercicios {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_POR_TIPO = 2;

    private ValidadorDeEjercicios() {
    }

    public static List<PracticeGenerator.Generado> validos(List<PracticeGenerator.Generado> generados,
                                                         Material material, int cuantos) {
        Set<String> terminos = new HashSet<>();
        material.vocabulary().forEach(t -> terminos.add(norma(t.term())));
        Map<PracticeItemType, Integer> porTipo = new EnumMap<>(PracticeItemType.class);
        List<PracticeGenerator.Generado> salida = new ArrayList<>();
        for (PracticeGenerator.Generado g : generados == null ? List.<PracticeGenerator.Generado>of() : generados) {
            if (salida.size() >= cuantos) {
                break;
            }
            if (g == null || g.tipo() == null || porTipo.getOrDefault(g.tipo(), 0) >= MAX_POR_TIPO) {
                continue;
            }
            if (valido(g, material, terminos)) {
                salida.add(g);
                porTipo.merge(g.tipo(), 1, Integer::sum);
            }
        }
        return salida;
    }

    static boolean valido(PracticeGenerator.Generado g, Material material, Set<String> terminos) {
        // Los largos son los de la tabla: algo que no cabe tumbaría el set entero al guardarlo.
        if (vacio(g.prompt()) || g.prompt().length() > 600 || vacio(g.explicacion()) || g.explicacion().length() > 400
                || (g.expected() != null && g.expected().length() > 600)
                || (g.terminoFuente() != null && g.terminoFuente().length() > 120)) {
            return false;
        }
        try {
            JsonNode p = JSON.readTree(g.payload());
            return switch (g.tipo()) {
                case FILL_BLANK -> {
                    String t = norma(g.terminoFuente());
                    List<String> opciones = new ArrayList<>();
                    p.path("options").forEach(o -> opciones.add(norma(o.asText())));
                    // Anclar al acta admite la pista entre paréntesis; que se pueda acertar, no: entre
                    // las opciones tiene que estar la esperada tal como la compara el Evaluador.
                    Set<String> comoLasCompara = new HashSet<>();
                    p.path("options").forEach(o -> comoLasCompara.add(Evaluador.normalizar(o.asText())));
                    yield terminos.contains(t) && t.equals(norma(g.expected()))
                            && p.path("sentence").asText("").contains("___")
                            && opciones.size() >= 2 && opciones.size() <= 4 && opciones.contains(t)
                            && comoLasCompara.contains(Evaluador.normalizar(g.expected()));
                }
                case MATCH_MEANING -> {
                    JsonNode esperado = JSON.readTree(g.expected());
                    List<String> ts = new ArrayList<>();
                    p.path("terms").forEach(x -> ts.add(norma(x.asText())));
                    // Y los significados que se esperan son justo los que se ofrecen: si el modelo
                    // parafrasea uno («de repente» contra «repentinamente»), no habría forma de acertar.
                    Set<String> ofrecidos = new HashSet<>();
                    p.path("meanings").forEach(x -> ofrecidos.add(Evaluador.normalizar(x.asText())));
                    Set<String> esperados = new HashSet<>();
                    esperado.forEach(x -> esperados.add(Evaluador.normalizar(x.asText())));
                    // Y las claves de la respuesta son los términos que se muestran, tal como los compara
                    // el Evaluador: la pantalla arma la respuesta con los términos del payload.
                    Set<String> mostrados = new HashSet<>();
                    p.path("terms").forEach(x -> mostrados.add(Evaluador.normalizar(x.asText())));
                    Set<String> claves = new HashSet<>();
                    esperado.fieldNames().forEachRemaining(k -> claves.add(Evaluador.normalizar(k)));
                    yield ts.size() >= 2 && ts.size() <= 5 && terminos.containsAll(ts)
                            && p.path("meanings").size() == ts.size() && esperado.size() == ts.size()
                            && claves.equals(mostrados) && esperados.equals(ofrecidos);
                }
                case FIX_SENTENCE -> !vacio(material.recurringIssues()) && !vacio(p.path("sentence").asText(null))
                        && !vacio(g.expected()) && !norma(g.expected()).equals(norma(p.path("sentence").asText()));
                case ORDER_DIALOGUE -> {
                    JsonNode esperado = JSON.readTree(g.expected());
                    List<String> lineas = new ArrayList<>();
                    List<String> enOrden = new ArrayList<>();
                    p.path("lines").forEach(x -> lineas.add(x.asText()));
                    esperado.forEach(x -> enOrden.add(x.asText()));
                    yield !vacio(material.workedOn()) && lineas.size() >= 3 && lineas.size() <= 6
                            && new HashSet<>(lineas).equals(new HashSet<>(enOrden)) && lineas.size() == enOrden.size()
                            && !lineas.equals(enOrden);
                }
                case WRITE_SENTENCE -> terminos.contains(norma(p.path("term").asText("")));
            };
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Sin mayúsculas ni la pista entre paréntesis: «get used to (+ing)» en el acta es el mismo término
     * que el «get used to» que escribe el modelo, como ya lo entiende {@code Evaluador}.
     */
    private static String norma(String s) {
        return s == null ? "" : s.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
