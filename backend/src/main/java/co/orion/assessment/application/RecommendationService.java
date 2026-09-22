package co.orion.assessment.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.assessment.domain.ReasonCode;
import co.orion.assessment.domain.Recomendacion;
import co.orion.catalog.persistence.TeachingGoalRepository;
import co.orion.identity.api.LanguageBadge;
import co.orion.identity.api.ProfessorCard;
import co.orion.identity.application.ProfessorSearchCriteria;
import co.orion.identity.application.ProfessorSearchService;
import co.orion.identity.application.ProfessorSortOption;
import co.orion.reputation.domain.ProfessorMetrics;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.scheduling.application.SlotQueryService;
import co.orion.shared.time.BusinessZone;

/**
 * Los tres profesores que se recomiendan al terminar el diagnóstico.
 *
 * <p><strong>Esto no lo decide la IA.</strong> Es una consulta determinista, y la razón no es
 * técnica: una recomendación generada libremente puede afirmar cosas falsas sobre una persona real
 * —«se especializa en negocios» de alguien que no— y eso no es un fallo de producto, es una
 * afirmación con nuestro membrete sobre el trabajo de un tercero.
 *
 * <p><strong>No se inventa un ranking nuevo.</strong> El filtro es el del buscador, que ya resuelve
 * publicado, aprobado, tarifa, idioma, nivel, objetivo y sanciones; el orden es el
 * {@code ranking_score} de {@code reputation}, que ya resuelve el arranque en frío con puntaje
 * neutro. Duplicar cualquiera de las dos cosas aquí sería tener dos definiciones de «buen profesor»
 * que se separan con el tiempo.
 *
 * <p><strong>Con agenda de verdad.</strong> El último filtro se aplica sobre los candidatos ya
 * ordenados y no dentro de la consulta: calcular cupos libres por profesor es caro y el buscador lo
 * evita a propósito, pero aquí solo hacen falta tres, así que se comprueban unos pocos. Recomendar
 * a alguien sin agenda es mandar a la persona a un callejón sin salida justo en el momento en que
 * más ganas tiene de reservar.
 *
 * <p><strong>Siempre tres</strong>, si la plataforma los tiene (decisión de Pardo, 22/09/2026).
 * Antes se devolvían los que hubiera, y la persona que acababa de hablar dos minutos leía «todavía
 * no tenemos tres para ti»: una puerta cerrada justo cuando más ganas tenía. Ahora se completa por
 * escalones —primero los que encajan y tienen agenda, después cualquiera del idioma con agenda, y
 * al final sin mirar agenda—, y lo que no se relaja nunca es la verdad de la razón: a quien entra
 * por relleno no se le atribuye un encaje que no tiene. Para eso está {@link ReasonCode#VERIFIED}.
 */
@Service
public class RecommendationService {

    /** Cuántos días hacia delante se mira para decidir si un profesor tiene agenda. */
    static final int DIAS_DE_AGENDA = 7;

    /** Cuántos candidatos se traen antes de comprobar agenda. Tres salen de aquí. */
    private static final int CANDIDATOS = 12;

    static final int CUANTAS = 3;

    private final ProfessorSearchService buscador;
    private final ProfessorMetricsRepository metrics;
    private final SlotQueryService cupos;
    private final TeachingGoalRepository objetivos;
    private final Clock clock;

    public RecommendationService(ProfessorSearchService buscador,
                                 ProfessorMetricsRepository metrics,
                                 SlotQueryService cupos,
                                 TeachingGoalRepository objetivos,
                                 Clock clock) {
        this.buscador = buscador;
        this.metrics = metrics;
        this.cupos = cupos;
        this.objetivos = objetivos;
        this.clock = clock;
    }

    /**
     * @param idioma       el idioma evaluado
     * @param nivel        el nivel inferido del diagnóstico (BEGINNER / INTERMEDIATE / ADVANCED)
     * @param objetivosDeLaPersona lo que dijo que quiere hacer con el idioma; puede venir vacío
     */
    @Transactional(readOnly = true)
    public List<Recomendacion> para(String idioma, String nivel, List<String> objetivosDeLaPersona) {
        List<String> metas = objetivosDeLaPersona == null ? List.of() : objetivosDeLaPersona;
        Map<String, String> nombreDelObjetivo = objetivos.findByActiveTrueOrderByDisplayOrderAsc()
                .stream().collect(Collectors.toMap(g -> g.getCode(), g -> g.getNameEs(), (a, b) -> a));
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);

        List<ProfessorCard> queEncajan = ordenados(buscar(idioma,
                nivel == null ? List.of() : List.of(nivel), metas));
        List<ProfessorCard> delIdioma = ordenados(buscar(idioma, List.of(), List.of()));

        List<Recomendacion> elegidos = new ArrayList<>();
        // Escalón 1: los que encajan y tienen agenda. Escalón 2: cualquiera del idioma con agenda.
        // Escalón 3: cualquiera del idioma, aunque no tenga agenda esta semana.
        elegir(queEncajan, true, hoy, idioma, nivel, metas, nombreDelObjetivo, elegidos);
        elegir(delIdioma, true, hoy, idioma, nivel, metas, nombreDelObjetivo, elegidos);
        elegir(delIdioma, false, hoy, idioma, nivel, metas, nombreDelObjetivo, elegidos);
        return elegidos;
    }

    private List<ProfessorCard> buscar(String idioma, List<String> niveles, List<String> metas) {
        ProfessorSearchCriteria criterios = new ProfessorSearchCriteria(
                idioma, niveles, metas,
                null, null, null, null,
                java.util.Set.of(), null, null,
                List.of(), null);
        return buscador.search(criterios, ProfessorSortOption.RELEVANCE, 0, CANDIDATOS).content();
    }

    /**
     * El orden lo pone reputation. Quien no tenga métricas todavía queda al final, no fuera: un
     * profesor nuevo no es peor, simplemente no ha demostrado nada aún.
     */
    private List<ProfessorCard> ordenados(List<ProfessorCard> candidatos) {
        if (candidatos.isEmpty()) {
            return candidatos;
        }
        Map<UUID, BigDecimal> puntajes = metrics
                .findByProfessorIdIn(candidatos.stream().map(ProfessorCard::id).toList()).stream()
                .filter(m -> m.getRankingScore() != null)
                .collect(Collectors.toMap(ProfessorMetrics::getProfessorId,
                        ProfessorMetrics::getRankingScore, (a, b) -> a));
        return candidatos.stream()
                .sorted(Comparator
                        .comparing((ProfessorCard c) -> puntajes.getOrDefault(c.id(), BigDecimal.ZERO))
                        .reversed()
                        .thenComparing(ProfessorCard::id))
                .toList();
    }

    private void elegir(List<ProfessorCard> candidatos, boolean exigirAgenda, LocalDate hoy,
                        String idioma, String nivel, List<String> metas,
                        Map<String, String> nombreDelObjetivo, List<Recomendacion> elegidos) {
        for (ProfessorCard candidato : candidatos) {
            if (elegidos.size() == CUANTAS) {
                return;
            }
            if (elegidos.stream().anyMatch(r -> r.professorId().equals(candidato.id()))) {
                continue;
            }
            boolean tieneAgenda = !cupos.availableSlots(
                    candidato.id(), hoy, hoy.plusDays(DIAS_DE_AGENDA)).isEmpty();
            if (exigirAgenda && !tieneAgenda) {
                continue;
            }
            elegidos.add(razonar(candidato, elegidos.size() + 1, idioma, nivel, metas,
                    tieneAgenda, nombreDelObjetivo));
        }
    }

    /**
     * La razón de mayor prioridad que aplique, redactada por plantilla.
     *
     * <p>Todo lo interpolado sale del perfil: el nombre del profesor, el nombre del objetivo del
     * catálogo, el idioma. Nada de esto lo escribe un modelo.
     */
    private Recomendacion razonar(ProfessorCard profesor,
                                  int posicion,
                                  String idioma,
                                  String nivel,
                                  List<String> objetivosDeLaPersona,
                                  boolean tieneAgenda,
                                  Map<String, String> nombreDelObjetivo) {
        String nombre = primerNombre(profesor.fullName());

        String objetivoComun = objetivosDeLaPersona == null ? null : objetivosDeLaPersona.stream()
                .filter(profesor.goals()::contains)
                .findFirst().orElse(null);
        if (objetivoComun != null) {
            String queHace = nombreDelObjetivo.getOrDefault(objetivoComun, "").toLowerCase();
            return new Recomendacion(profesor.id(), posicion, ReasonCode.GOAL_MATCH,
                    nombre + " trabaja justo con estudiantes que buscan " + queHace + ".");
        }

        // Se comprueba contra el nivel pedido y no contra «tiene niveles»: un profesor que entró de
        // relleno no enseña necesariamente desde donde está esta persona.
        if (nivel != null && profesor.levels().contains(nivel)) {
            return new Recomendacion(profesor.id(), posicion, ReasonCode.LEVEL_MATCH,
                    "Enseña desde el nivel en el que estás hoy.");
        }

        boolean nativo = profesor.languages().stream()
                .anyMatch(l -> l.isNative() && l.code().equalsIgnoreCase(idioma));
        if (nativo) {
            String nombreIdioma = profesor.languages().stream()
                    .filter(l -> l.code().equalsIgnoreCase(idioma))
                    .map(LanguageBadge::nameEs).findFirst().orElse(idioma);
            return new Recomendacion(profesor.id(), posicion, ReasonCode.NATIVE,
                    "Es hablante nativo de " + nombreIdioma.toLowerCase() + ".");
        }

        if (profesor.headline() != null && !profesor.headline().isBlank()) {
            return new Recomendacion(profesor.id(), posicion, ReasonCode.SPECIALTY,
                    "Se especializa en " + profesor.headline().trim() + ".");
        }

        if (tieneAgenda) {
            return new Recomendacion(profesor.id(), posicion, ReasonCode.SCHEDULE_MATCH,
                    "Tiene cupos libres esta semana.");
        }

        return new Recomendacion(profesor.id(), posicion, ReasonCode.VERIFIED,
                "Profesor verificado por Orión: documentos, experiencia y entrevista.");
    }

    private static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Tu profesor";
        }
        int espacio = nombre.trim().indexOf(' ');
        return espacio < 0 ? nombre.trim() : nombre.trim().substring(0, espacio);
    }

}
