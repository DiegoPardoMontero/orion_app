package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorGoalRepository;
import co.orion.identity.persistence.ProfessorLanguageLevelRepository;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.support.ApiIntegrationSupport;

/** Buscador del marketplace: cada filtro por separado, combinados, sin resultados, paginación, orden. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ProfessorSearchIT extends ApiIntegrationSupport {

    private static final String PROFESSORS = "/api/v1/professors";

    @Autowired private ProfessorProfileRepository profiles;
    @Autowired private ProfessorLanguageRepository languages;
    @Autowired private ProfessorLanguageLevelRepository levels;
    @Autowired private ProfessorGoalRepository goals;
    @Autowired private JdbcTemplate jdbc;

    private UUID ingrid;
    private UUID franco;
    private UUID beatriz;

    @BeforeEach
    void seed() {
        levels.deleteAll();
        languages.deleteAll();
        goals.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        // P1: inglés (básico/intermedio), conversación, 30.000, certificado.
        UUID p1 = professor("ingles@orion.test", "Ingrid Inglés", 30000, true, true);
        ingrid = p1;
        teaches(p1, "EN", false, "BEGINNER", "INTERMEDIATE");
        aims(p1, "CONVERSATION");

        // P2: francés (avanzado), negocios, 80.000, no certificado.
        UUID p2 = professor("frances@orion.test", "Franco Francés", 80000, false, true);
        franco = p2;
        teaches(p2, "FR", true, "ADVANCED");
        aims(p2, "BUSINESS");

        // P3: inglés (nativo) + español, viajes, 50.000.
        UUID p3 = professor("bilingue@orion.test", "Beatriz Bilingüe", 50000, false, true);
        beatriz = p3;
        teaches(p3, "EN", true, "ADVANCED");
        teaches(p3, "ES", false, "BEGINNER");
        aims(p3, "TRAVEL");

        // P4: publicado NO — nunca debe aparecer aunque tenga idioma y tarifa.
        UUID p4 = professor("oculto@orion.test", "Óscar Oculto", 40000, false, false);
        teaches(p4, "EN", false, "BEGINNER");
        aims(p4, "CONVERSATION");
    }

    private PagedProfessors search(String query) {
        ResponseEntity<PagedProfessors> r = rest.getForEntity(PROFESSORS + query, PagedProfessors.class);
        return r.getBody();
    }

    @Test
    void filtersByLanguage() {
        assertThat(search("?language=EN").totalElements()).isEqualTo(2);
        assertThat(search("?language=FR").totalElements()).isEqualTo(1);
        assertThat(search("?language=ES").totalElements()).isEqualTo(1);
    }

    @Test
    void filtersByGoal() {
        assertThat(search("?goal=BUSINESS").totalElements()).isEqualTo(1);
        assertThat(search("?goal=CONVERSATION").totalElements()).isEqualTo(1);
        // OR entre objetivos: conversación o negocios.
        assertThat(search("?goal=CONVERSATION,BUSINESS").totalElements()).isEqualTo(2);
    }

    @Test
    void filtersByLevel() {
        assertThat(search("?level=ADVANCED").totalElements()).isEqualTo(2);
        assertThat(search("?level=BEGINNER").totalElements()).isEqualTo(2);
    }

    @Test
    void filtersByPrice() {
        assertThat(search("?minPrice=40000").totalElements()).isEqualTo(2);
        assertThat(search("?maxPrice=40000").totalElements()).isEqualTo(1);
    }

    @Test
    void combinesFilters() {
        // Inglés y hasta 40.000: solo P1.
        PagedProfessors r = search("?language=EN&maxPrice=40000");
        assertThat(r.totalElements()).isEqualTo(1);
        assertThat(r.content().get(0).fullName()).isEqualTo("Ingrid Inglés");
    }

    @Test
    void filtersByCertifiedAndNative() {
        assertThat(search("?certified=true").totalElements()).isEqualTo(1);
        // Inglés nativo: solo P3.
        PagedProfessors nativeEn = search("?language=EN&native=true");
        assertThat(nativeEn.totalElements()).isEqualTo(1);
        assertThat(nativeEn.content().get(0).fullName()).isEqualTo("Beatriz Bilingüe");
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        assertThat(search("?language=EN&minPrice=100000").totalElements()).isZero();
    }

    @Test
    void neverReturnsAnUnpublishedProfessor() {
        // 3 publicados; el oculto (P4) nunca cuenta.
        assertThat(search("").totalElements()).isEqualTo(3);
    }

    @Test
    void paginates() {
        PagedProfessors first = search("?size=2&page=0");
        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(search("?size=2&page=1").content()).hasSize(1);
    }

    @Test
    void sortsByPriceAscending() {
        PagedProfessors r = search("?sort=PRICE_ASC");
        assertThat(r.content().get(0).hourlyRateCop()).isEqualTo(30000L);
        assertThat(r.content().get(r.content().size() - 1).hourlyRateCop()).isEqualTo(80000L);
    }

    /**
     * RELEVANCE sigue el puntaje de ranking que «Desempeño» le explica al profe, en la consulta y no
     * en la página: con páginas de uno, cada una trae al siguiente del orden global. Quien todavía no
     * tiene puntaje va al final aunque esté certificado.
     */
    @Test
    void relevanceFollowsTheRankingScoreAcrossPages() {
        metrics(franco, "80.00", null, 0);
        metrics(beatriz, "50.00", null, 0);

        assertThat(names("?size=1&page=0")).containsExactly("Franco Francés");
        assertThat(names("?size=1&page=1")).containsExactly("Beatriz Bilingüe");
        assertThat(names("?size=1&page=2")).containsExactly("Ingrid Inglés");
        assertThat(names("")).containsExactly("Franco Francés", "Beatriz Bilingüe", "Ingrid Inglés");
        assertThat(search("?size=1").totalElements()).isEqualTo(3);
    }

    /** Sin puntajes (antes de la primera pasada del job nocturno): certificados y luego por nombre. */
    @Test
    void withoutScoresCertifiedComeFirstAndThenByName() {
        assertThat(names("")).containsExactly("Ingrid Inglés", "Beatriz Bilingüe", "Franco Francés");
    }

    /**
     * «Mejor calificados» ordena por el promedio que se exhibe, en toda la lista: el 5,0 de Ingrid
     * sale de dos reseñas y no se muestra, así que va al final.
     */
    @Test
    void ratingSortsByTheShownAverageAcrossPages() {
        metrics(beatriz, "10.00", "4.90", 5);
        metrics(franco, "90.00", "4.20", 10);
        metrics(ingrid, "95.00", "5.00", 2);

        assertThat(names("?sort=RATING&size=1&page=0")).containsExactly("Beatriz Bilingüe");
        assertThat(names("?sort=RATING&size=1&page=1")).containsExactly("Franco Francés");
        assertThat(names("?sort=RATING&size=1&page=2")).containsExactly("Ingrid Inglés");
    }

    private List<String> names(String query) {
        return search(query).content().stream().map(ProfessorCard::fullName).toList();
    }

    private void metrics(UUID professorId, String rankingScore, String ratingAvg, int ratingCount) {
        jdbc.update("""
                insert into professor_metrics (professor_id, ranking_score, rating_avg, rating_count)
                values (?, ?, ?, ?)
                """, professorId, new BigDecimal(rankingScore),
                ratingAvg == null ? null : new BigDecimal(ratingAvg), ratingCount);
    }

    // --- helpers de siembra ---

    private UUID professor(String email, String name, long rate, boolean certified, boolean published) {
        User u = createUser(email, name, UserRole.PROFESSOR);
        ProfessorProfile p = new ProfessorProfile(u);
        // Titular y descripción cumplen los mínimos de palabras que exige el dominio.
        p.describe(name + " enseña idiomas en Orión",
                "Perfil de prueba de " + name + ". Da clases en vivo, prepara cada sesión "
                        + "con antelación y adapta el ritmo a lo que necesita cada estudiante "
                        + "que reserva con ella.");
        p.enrich("CO", "Bogotá", null, (short) 3, null, certified, true);
        p.changeRate(rate);
        if (published) {
            p.publish();
        }
        profiles.save(p);
        // El gate exige postulación APPROVED; se la damos a todos para aislar los filtros del buscador.
        approveTeacher(u.getId());
        return u.getId();
    }

    private void teaches(UUID id, String code, boolean nativeLang, String... lvls) {
        languages.save(new ProfessorLanguage(id, code, nativeLang));
        for (String level : lvls) {
            levels.save(new ProfessorLanguageLevel(id, code, level));
        }
    }

    private void aims(UUID id, String... goalCodes) {
        for (String goal : goalCodes) {
            goals.save(new ProfessorGoal(id, goal));
        }
    }
}
