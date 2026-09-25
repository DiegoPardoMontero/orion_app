package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El filtro por día y franja. Es público: se busca profesor antes de tener cuenta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class BusquedaPorDisponibilidadIT extends ApiIntegrationSupport {

    @Autowired
    private ProfessorProfileRepository perfiles;

    @Autowired
    private AvailabilityRuleRepository reglas;

    @BeforeEach
    void seed() {
        reglas.deleteAll();
        perfiles.deleteAll();
        users.deleteAll();

        // María enseña martes por la noche; Carlos, martes por la mañana; Juan, sábados.
        publicar("maria@orion.test", "María Gómez", DayOfWeek.TUESDAY, "18:00", "21:00");
        publicar("carlos@orion.test", "Carlos Peña", DayOfWeek.TUESDAY, "08:00", "12:00");
        publicar("juan@orion.test", "Juan Torres", DayOfWeek.SATURDAY, "08:00", "12:00");
        // Sofía tiene media hora suelta el martes por la noche: nunca daría una clase de 60 min.
        publicar("sofia@orion.test", "Sofía Ruiz", DayOfWeek.TUESDAY, "20:45", "21:15");
    }

    private void publicar(String email, String nombre, DayOfWeek dia, String desde, String hasta) {
        User profesor = createUser(email, nombre, UserRole.PROFESSOR);
        approveTeacher(profesor.getId());
        ProfessorProfile perfil = new ProfessorProfile(profesor);
        perfil.changeRate(45000L);
        perfil.publish();
        perfiles.saveAndFlush(perfil);
        reglas.saveAndFlush(new AvailabilityRule(profesor.getId(), dia,
                LocalTime.parse(desde), LocalTime.parse(hasta)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> nombresDe(ResponseEntity<Map> respuesta) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) respuesta.getBody().get("content");
        return items.stream().map(i -> String.valueOf(i.get("fullName"))).toList();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin filtro salen todos, incluida la de la franja corta")
    void sinFiltroSalenTodos() {
        ResponseEntity<Map> respuesta = rest.getForEntity("/api/v1/professors", Map.class);

        assertThat(nombresDe(respuesta)).hasSize(4);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Filtrar por martes deja fuera a quien solo da sábados")
    void filtrarPorDia() {
        ResponseEntity<Map> respuesta =
                rest.getForEntity("/api/v1/professors?day=TUESDAY", Map.class);

        assertThat(nombresDe(respuesta)).containsExactlyInAnyOrder("María Gómez", "Carlos Peña");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El día y la franja se combinan: martes por la noche no es martes por la mañana")
    void elDiaYLaFranjaSeCombinan() {
        ResponseEntity<Map> respuesta =
                rest.getForEntity("/api/v1/professors?day=TUESDAY&from=18:00&to=21:00", Map.class);

        assertThat(nombresDe(respuesta)).containsExactly("María Gómez");
    }

    /**
     * El caso que justifica que el filtro no sea un simple solapamiento. La franja de Sofía toca la
     * noche del martes y aun así nunca produciría un cupo, porque las clases duran 60 minutos. Un
     * filtro que la devolviera mandaría a la gente a un perfil donde no se puede reservar.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una franja más corta que una clase nunca cuenta como disponible")
    void laFranjaCortaNuncaCuenta() {
        assertThat(nombresDe(rest.getForEntity("/api/v1/professors?day=TUESDAY", Map.class)))
                .doesNotContain("Sofía Ruiz");
        assertThat(nombresDe(rest.getForEntity(
                "/api/v1/professors?day=TUESDAY&from=20:00&to=22:00", Map.class)))
                .doesNotContain("Sofía Ruiz");
    }

    /**
     * Nulo y lista vacía no son lo mismo: si nadie cumple, el resultado es vacío. Devolver el
     * catálogo entero justo cuando alguien acaba de pedir algo muy concreto sería lo contrario
     * de un filtro.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Si nadie cumple el filtro, no sale nadie")
    void siNadieCumpleNoSaleNadie() {
        ResponseEntity<Map> respuesta =
                rest.getForEntity("/api/v1/professors?day=SUNDAY", Map.class);

        assertThat(nombresDe(respuesta)).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("La franja sola, sin día, filtra por hora en cualquier día")
    void soloLaFranja() {
        ResponseEntity<Map> respuesta =
                rest.getForEntity("/api/v1/professors?from=08:00&to=12:00", Map.class);

        assertThat(nombresDe(respuesta)).containsExactlyInAnyOrder("Juan Torres", "Carlos Peña");
    }

    /**
     * Horas exactas, varias a la vez (24/09/2026). «Las 7 o las 19» junta a quien puede a las 7 de la
     * mañana con quien puede a las 7 de la noche; y una hora es el cupo en punto, así que la media
     * hora suelta de Sofía no cuenta ni pidiendo las 20 ni las 21.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Varias horas exactas a la vez: basta con que pueda a una")
    void variasHorasExactas() {
        assertThat(nombresDe(rest.getForEntity("/api/v1/professors?hour=08:00&hour=19:00", Map.class)))
                .containsExactlyInAnyOrder("María Gómez", "Carlos Peña", "Juan Torres");
        assertThat(nombresDe(rest.getForEntity("/api/v1/professors?hour=19:00&hour=20:00&hour=21:00", Map.class)))
                .containsExactly("María Gómez");
        // Con el día: el martes a las 8 es Carlos, no Juan (que da sábados).
        assertThat(nombresDe(rest.getForEntity("/api/v1/professors?day=TUESDAY&hour=8", Map.class)))
                .containsExactly("Carlos Peña");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("La última hora de una franja no cuenta si la clase no alcanza a caber")
    void laUltimaHoraNoCabe() {
        // La franja de María cierra a las 21:00: una clase a las 21 terminaría a las 21:55.
        assertThat(nombresDe(rest.getForEntity("/api/v1/professors?hour=21:00", Map.class))).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una hora que no es en punto responde 422: los cupos empiezan en punto")
    void unaHoraSinPuntoEs422() {
        ResponseEntity<Map> respuesta = rest.getForEntity("/api/v1/professors?hour=18:30", Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(rest.getForEntity("/api/v1/professors?hour=tarde", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un día que no existe responde 422 con los válidos, no un 500")
    void unDiaInventadoEs422() {
        ResponseEntity<Map> respuesta =
                rest.getForEntity("/api/v1/professors?day=LUNES", Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(respuesta.getBody().get("error"))).contains("MONDAY");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una hora mal escrita responde 422 y dice el formato")
    void unaHoraMalEscritaEs422() {
        ResponseEntity<Map> respuesta =
                rest.getForEntity("/api/v1/professors?from=6pm", Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(respuesta.getBody().get("error"))).contains("HH:mm");
    }
}
