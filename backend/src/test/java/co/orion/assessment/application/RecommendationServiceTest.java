package co.orion.assessment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.assessment.domain.ReasonCode;
import co.orion.assessment.domain.Recomendacion;
import co.orion.catalog.domain.TeachingGoal;
import co.orion.catalog.persistence.TeachingGoalRepository;
import co.orion.identity.api.LanguageBadge;
import co.orion.identity.api.PagedProfessors;
import co.orion.identity.api.ProfessorCard;
import co.orion.identity.application.ProfessorSearchCriteria;
import co.orion.identity.application.ProfessorSearchService;
import co.orion.identity.application.ProfessorSortOption;
import co.orion.reputation.domain.ProfessorMetrics;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.scheduling.application.SlotQueryService;
import co.orion.scheduling.domain.Slot;

/**
 * Las tres recomendaciones: deterministas, ordenadas por reputación y siempre tres.
 *
 * <p>Lo que más importa de este test es lo que impide. Desde el 22/09/2026 se completa hasta tres
 * (decisión de Pardo), pero por escalones y sin mentir: quien entra de relleno va detrás de quien
 * encaja, quien no tiene agenda va detrás de quien sí, y la razón de cada uno dice solo lo que es
 * cierto de él. El texto sale de plantilla y del perfil real, nunca de un modelo.
 */
class RecommendationServiceTest {

    private static final String IDIOMA = "EN";
    private static final Instant AHORA = Instant.parse("2026-09-16T15:00:00Z");

    private final ProfessorSearchService buscador = mock(ProfessorSearchService.class);
    private final ProfessorMetricsRepository metrics = mock(ProfessorMetricsRepository.class);
    private final SlotQueryService cupos = mock(SlotQueryService.class);
    private final TeachingGoalRepository objetivos = mock(TeachingGoalRepository.class);

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(buscador, metrics, cupos, objetivos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(metrics.findByProfessorIdIn(any())).thenReturn(List.of());
        when(objetivos.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of());
        // Por defecto, todos tienen agenda. Cada test apaga la de quien le interese.
        when(cupos.availableSlots(any(), any(), any())).thenReturn(List.of(mock(Slot.class)));
    }

    private ProfessorCard profesor(String nombre, List<String> metas, boolean nativo, String titular) {
        return profesor(nombre, metas, nativo, titular, List.of("BEGINNER"));
    }

    private ProfessorCard profesor(String nombre, List<String> metas, boolean nativo, String titular,
                                   List<String> niveles) {
        return new ProfessorCard(UUID.randomUUID(), nombre, null, titular, "Bogotá", "CO",
                false, 60_000L, null, 0,
                List.of(new LanguageBadge(IDIOMA, "Inglés", "English", "🇬🇧", nativo)),
                niveles, metas);
    }

    /** Lo que devuelve el buscador cuando se le piden objetivos, y cuando se le pide solo el idioma. */
    private void devuelvePorEscalon(List<ProfessorCard> queEncajan, List<ProfessorCard> delIdioma) {
        when(buscador.search(argThat(c -> c != null && !c.goals().isEmpty()),
                eq(ProfessorSortOption.RELEVANCE), anyInt(), anyInt()))
                .thenReturn(new PagedProfessors(queEncajan, 0, queEncajan.size(), queEncajan.size(), 1));
        when(buscador.search(argThat(c -> c != null && c.goals().isEmpty()),
                eq(ProfessorSortOption.RELEVANCE), anyInt(), anyInt()))
                .thenReturn(new PagedProfessors(delIdioma, 0, delIdioma.size(), delIdioma.size(), 1));
    }

    private void devuelve(ProfessorCard... cards) {
        when(buscador.search(any(ProfessorSearchCriteria.class), eq(ProfessorSortOption.RELEVANCE),
                anyInt(), anyInt()))
                .thenReturn(new PagedProfessors(List.of(cards), 0, cards.length, cards.length, 1));
    }

    private void puntaje(ProfessorCard card, double valor) {
        ProfessorMetrics m = mock(ProfessorMetrics.class);
        when(m.getProfessorId()).thenReturn(card.id());
        when(m.getRankingScore()).thenReturn(BigDecimal.valueOf(valor));
        when(metrics.findByProfessorIdIn(any())).thenReturn(List.of(m));
    }

    @Test
    @DisplayName("Sin candidatos no se recomienda a nadie, y no pasa nada")
    void sinCandidatosNoHayRecomendaciones() {
        devuelve();
        assertThat(service.para(IDIOMA, "BEGINNER", List.of())).isEmpty();
    }

    @Test
    @DisplayName("Con dos que encajan se completa hasta tres con otro del idioma, detrás de ellos")
    void seCompletaHastaTres() {
        ProfessorCard ana = profesor("Ana Ruiz", List.of("WORK"), false, null);
        ProfessorCard beto = profesor("Beto Cruz", List.of("WORK"), false, null);
        ProfessorCard cami = profesor("Cami Díaz", List.of(), false, null);
        devuelvePorEscalon(List.of(ana, beto), List.of(cami, ana, beto));

        List<Recomendacion> r = service.para(IDIOMA, "BEGINNER", List.of("WORK"));

        // Entre Ana y Beto desempata el id (sin métricas, los dos puntúan igual); lo que importa es
        // que los dos que encajan van delante y el relleno, detrás.
        assertThat(r).hasSize(3);
        assertThat(r.subList(0, 2)).extracting(Recomendacion::professorId)
                .containsExactlyInAnyOrder(ana.id(), beto.id());
        assertThat(r.get(2).professorId()).isEqualTo(cami.id());
    }

    @Test
    @DisplayName("A quien entra de relleno no se le atribuye un encaje que no tiene")
    void elRellenoNoMiente() {
        // Sin objetivo común, sin el nivel pedido, sin ser nativo, sin titular y sin agenda: lo
        // único cierto que se puede decir de él es que está verificado.
        ProfessorCard ana = profesor("Ana Ruiz", List.of("WORK"), false, null);
        ProfessorCard relleno = profesor("Beto Cruz", List.of(), false, null, List.of("ADVANCED"));
        devuelvePorEscalon(List.of(ana), List.of(ana, relleno));
        when(cupos.availableSlots(eq(relleno.id()), any(), any())).thenReturn(List.of());

        Recomendacion segunda = service.para(IDIOMA, "BEGINNER", List.of("WORK")).get(1);

        assertThat(segunda.professorId()).isEqualTo(relleno.id());
        assertThat(segunda.reasonCode()).isEqualTo(ReasonCode.VERIFIED);
    }

    @Test
    @DisplayName("Solo si la plataforma entera tiene menos de tres se devuelven los que haya")
    void menosDeTresSoloSiNoHayMas() {
        devuelve(profesor("Ana Ruiz", List.of(), false, null),
                 profesor("Beto Cruz", List.of(), false, null));

        assertThat(service.para(IDIOMA, "BEGINNER", List.of())).hasSize(2);
    }

    @Test
    @DisplayName("Nunca más de tres, aunque haya más candidatos")
    void comoMuchoTres() {
        devuelve(profesor("Ana", List.of(), false, null), profesor("Beto", List.of(), false, null),
                 profesor("Cami", List.of(), false, null), profesor("Dani", List.of(), false, null));

        assertThat(service.para(IDIOMA, "BEGINNER", List.of())).hasSize(3);
    }

    @Test
    @DisplayName("Quien no tiene cupos en la semana va detrás de quien sí, y no se le prometen")
    void sinAgendaVaAlFinal() {
        ProfessorCard sinAgenda = profesor("Ana", List.of(), false, null, List.of());
        ProfessorCard conAgenda = profesor("Beto", List.of(), false, null);
        devuelve(sinAgenda, conAgenda);
        when(cupos.availableSlots(eq(sinAgenda.id()), any(), any())).thenReturn(List.of());

        List<Recomendacion> r = service.para(IDIOMA, "BEGINNER", List.of());

        assertThat(r).extracting(Recomendacion::professorId)
                .containsExactly(conAgenda.id(), sinAgenda.id());
        assertThat(r.get(1).reasonCode()).isNotEqualTo(ReasonCode.SCHEDULE_MATCH);
    }

    @Test
    @DisplayName("Ordena por el ranking de reputation, no por el orden del buscador")
    void ordenaPorReputacion() {
        ProfessorCard primero = profesor("Ana", List.of(), false, null);
        ProfessorCard segundo = profesor("Beto", List.of(), false, null);
        devuelve(primero, segundo);
        puntaje(segundo, 9.5);

        assertThat(service.para(IDIOMA, "BEGINNER", List.of()).getFirst().professorId())
                .isEqualTo(segundo.id());
    }

    @Test
    @DisplayName("La razón de más prioridad es el objetivo compartido, y el texto sale del catálogo")
    void elObjetivoManda() {
        TeachingGoal meta = mock(TeachingGoal.class);
        when(meta.getCode()).thenReturn("WORK");
        when(meta.getNameEs()).thenReturn("Trabajo");
        when(objetivos.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(meta));
        devuelve(profesor("María Gómez", List.of("WORK"), true, "Inglés para negocios"));

        Recomendacion r = service.para(IDIOMA, "BEGINNER", List.of("WORK")).getFirst();

        assertThat(r.reasonCode()).isEqualTo(ReasonCode.GOAL_MATCH);
        // Primer nombre, y el objetivo tal como lo llama el catálogo: nada lo escribe un modelo.
        assertThat(r.reasonText()).isEqualTo("María trabaja justo con estudiantes que buscan trabajo.");
    }

    @Test
    @DisplayName("Sin objetivo común manda el nivel; el nativo solo si no hay niveles")
    void laPrioridadDeLasRazones() {
        devuelve(profesor("Ana Ruiz", List.of(), true, null));
        assertThat(service.para(IDIOMA, "BEGINNER", List.of("WORK")).getFirst().reasonCode())
                .isEqualTo(ReasonCode.LEVEL_MATCH);
    }

    @Test
    @DisplayName("Las posiciones salen 1, 2 y 3 en orden")
    void lasPosicionesSonCorrelativas() {
        devuelve(profesor("Ana", List.of(), false, null), profesor("Beto", List.of(), false, null),
                 profesor("Cami", List.of(), false, null));

        assertThat(service.para(IDIOMA, "BEGINNER", List.of()))
                .extracting(Recomendacion::position)
                .containsExactly(1, 2, 3);
    }
}
