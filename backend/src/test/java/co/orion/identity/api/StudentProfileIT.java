package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.StudentGoalRepository;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * La ficha del estudiante y, sobre todo, quién puede verla. La regla de visibilidad es la parte
 * delicada del paso: se aplica en el servidor, y cuando no hay derecho responde 404 y no 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, StudentProfileIT.FrozenClockConfiguration.class})
class StudentProfileIT extends ApiIntegrationSupport {

    private static final String MIA = "/api/v1/me/student-profile";
    /** Lunes 20 de julio de 2026: la edad se calcula contra esta fecha. */
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-20T17:00:00Z");

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private StudentProfileRepository profiles;

    @Autowired
    private StudentGoalRepository goals;

    @Autowired
    private BookingRepository bookings;

    private User ana;
    private User carlos;
    private User maria;
    private User pedro;
    private Session anaSession;
    private Session carlosSession;
    private Session mariaSession;
    private Session pedroSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        goals.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        pedro = createUser("pedro@orion.test", "Pedro Sin Relación", UserRole.PROFESSOR);

        anaSession = login("ana@orion.test");
        carlosSession = login("carlos@orion.test");
        mariaSession = login("maria@orion.test");
        pedroSession = login("pedro@orion.test");
    }

    private String perfilDe(User estudiante) {
        return "/api/v1/students/" + estudiante.getId() + "/profile";
    }

    private void claseCompartida(User estudiante, User profesor) {
        Instant inicio = ZonedDateTime
                .of(LocalDate.of(2026, 7, 15), LocalTime.of(18, 0), BusinessZone.BOGOTA).toInstant();
        bookings.save(TestBookings.confirmed(estudiante.getId(), profesor.getId(), inicio,
                BookingModality.VIRTUAL, null, estudiante.getId()));
    }

    /* ---- La ficha propia ---- */

    @Test
    void todoEstudianteTieneFichaAunqueNuncaLaHayaTocado() {
        ResponseEntity<StudentProfileResponse> response =
                get(MIA, anaSession, StudentProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isPublic()).isFalse();
        // Nace con los cosméticos iniciales: nunca hay un avatar sin definir.
        assertThat(response.getBody().frameCode()).isEqualTo("trazo");
        assertThat(response.getBody().skyCode()).isEqualTo("crema");
    }

    @Test
    void elEstudianteDeclaraSuNivelSuIdiomaYSusObjetivos() {
        ResponseEntity<StudentProfileResponse> response = put(MIA, anaSession,
                new StudentProfileRequest("INTERMEDIATE", "EN", "Quiero sostener una reunión.",
                        List.of("CONVERSATION", "BUSINESS")),
                StudentProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().selfDeclaredLevel()).isEqualTo("INTERMEDIATE");
        assertThat(response.getBody().primaryLanguage()).isEqualTo("EN");
        assertThat(response.getBody().goalCodes()).containsExactlyInAnyOrder("CONVERSATION", "BUSINESS");
    }

    /** El nivel es del estudiante: puede subirlo, bajarlo y quitarlo cuando quiera. */
    @Test
    void elNivelSePuedeCambiarYQuitar() {
        put(MIA, anaSession, new StudentProfileRequest("BEGINNER", null, null, List.of()),
                StudentProfileResponse.class);

        ResponseEntity<StudentProfileResponse> subido = put(MIA, anaSession,
                new StudentProfileRequest("ADVANCED", null, null, List.of()),
                StudentProfileResponse.class);
        assertThat(subido.getBody().selfDeclaredLevel()).isEqualTo("ADVANCED");

        ResponseEntity<StudentProfileResponse> vacio = put(MIA, anaSession,
                new StudentProfileRequest(null, null, null, List.of()),
                StudentProfileResponse.class);
        assertThat(vacio.getBody().selfDeclaredLevel()).isNull();
    }

    @SuppressWarnings("rawtypes")
    @Test
    void unNivelDesconocidoEs422ConNombres() {
        ResponseEntity<Map> response = put(MIA, anaSession,
                new StudentProfileRequest("EXPERTO", null, null, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /* ---- El perfil público ---- */

    /**
     * Desde el Bloque 9 activarlo no pide fecha de nacimiento: Orión solo acepta mayores de 18 y
     * eso se comprueba en el registro. Volver a pedir la fecha aquí sería cobrar un dato personal
     * por una regla que ya se cumplió antes — lo que prohíbe el principio de minimización.
     */
    @Test
    void activarElPerfilPublicoNoPideNadaMas() {
        ResponseEntity<StudentProfileResponse> response = put(MIA + "/visibility", anaSession,
                new StudentVisibilityRequest(true), StudentProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isPublic()).isTrue();
    }

    /** Retirar el consentimiento tiene que ser más fácil que darlo: no pide nada. */
    @Test
    void desactivarloNoPideNada() {
        put(MIA + "/visibility", anaSession,
                new StudentVisibilityRequest(true), StudentProfileResponse.class);

        ResponseEntity<StudentProfileResponse> response = put(MIA + "/visibility", anaSession,
                new StudentVisibilityRequest(false), StudentProfileResponse.class);

        assertThat(response.getBody().isPublic()).isFalse();
    }

    /* ---- Las tres capas de visibilidad ---- */

    @SuppressWarnings("rawtypes")
    @Test
    void unProfesorSinRelacionRecibe404YNo403() {
        ResponseEntity<Map> response = get(perfilDe(ana), pedroSession, Map.class);

        // 404 y no 403 a propósito: un 403 confirmaría que ese perfil existe.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unProfesorConUnaClaseCompartidaSiLoVe() {
        claseCompartida(ana, maria);

        ResponseEntity<StudentProfileResponse> response =
                get(perfilDe(ana), mariaSession, StudentProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().fullName()).isEqualTo("Ana Ramírez");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void otroEstudianteLoVeSoloSiElPerfilEsPublico() {
        ResponseEntity<Map> privado = get(perfilDe(ana), carlosSession, Map.class);
        assertThat(privado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        put(MIA + "/visibility", anaSession,
                new StudentVisibilityRequest(true),
                StudentProfileResponse.class);

        ResponseEntity<StudentProfileResponse> publico =
                get(perfilDe(ana), carlosSession, StudentProfileResponse.class);
        assertThat(publico.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void elDuenoSiempreVeElSuyoCompleto() {
        ResponseEntity<StudentProfileResponse> response =
                get(perfilDe(ana), anaSession, StudentProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().ownView()).isTrue();
    }

    /**
     * La vista pública se comprueba campo por campo. No basta con que el frontend no los pinte:
     * lo que no viaja no se puede filtrar mal.
     */
    @SuppressWarnings("rawtypes")
    @Test
    void laVistaPublicaNoLlevaCorreoNiTelefonoNiSaldo() {
        claseCompartida(ana, maria);

        ResponseEntity<Map> response = get(perfilDe(ana), mariaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> cuerpo = response.getBody();
        assertThat(cuerpo).doesNotContainKeys("email", "whatsappPhone", "balanceCop", "payments",
                "professors");
        // Ni siquiera en null: el ajuste de visibilidad es suyo. Y birthDate ya no existe:
        // el Bloque 9 lo borró por minimización, así que tampoco puede filtrarse.
        assertThat(cuerpo.get("isPublic")).isNull();
        assertThat(cuerpo).doesNotContainKey("birthDate");
    }
}
