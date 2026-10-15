package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.AdolescentProfileRepository;
import co.orion.identity.persistence.GuardianAuthorizationRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * Los adolescentes a cargo de un adulto (Bloque 12): el perfil nace con la autorización del
 * representante legal y su constancia; sin ella, o fuera de los 13 a 17, no nace; revocarla lo borra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AdolescentProfileIT extends ApiIntegrationSupport {

    private static final String RUTA = "/api/v1/me/adolescents";

    @Autowired private AdolescentProfileRepository profiles;
    @Autowired private GuardianAuthorizationRepository authorizations;
    @Autowired private JdbcTemplate jdbc;

    private Session ana;
    private Session maria;
    private int anoActual;

    @BeforeEach
    void seed() {
        jdbc.update("delete from guardian_authorizations");
        jdbc.update("delete from adolescent_profiles");
        users.deleteAll();
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = login("ana@orion.test");
        maria = login("maria@orion.test");
        anoActual = LocalDate.now().getYear();
    }

    private Map<String, Object> cuerpo(String nombre, int nacimiento, boolean autoriza, boolean escuchado) {
        return Map.of("firstName", nombre, "birthYear", nacimiento, "selfDeclaredLevel", "BEGINNER",
                "motivation", "Inglés para el colegio", "acceptsAuthorization", autoriza, "heardConfirmed", escuchado);
    }

    @Test
    @DisplayName("Crear el perfil deja la constancia de la autorización vigente, con IP y navegador")
    void crearConAutorizacion() {
        ResponseEntity<AdolescentProfileResponse> r = post(RUTA, ana,
                cuerpo("Sofi", anoActual - 15, true, true), AdolescentProfileResponse.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AdolescentProfileResponse creado = r.getBody();
        assertThat(creado.firstName()).isEqualTo("Sofi");
        assertThat(creado.age()).isEqualTo(15);
        assertThat(creado.authorizationVersion()).isEqualTo("1.0");
        assertThat(creado.authorizedAt()).isNotNull();

        var constancia = authorizations.findByAdolescentProfileIdAndRevokedAtIsNull(creado.id()).orElseThrow();
        assertThat(constancia.isHeardConfirmed()).isTrue();
        assertThat(constancia.getDocumentVersion()).isEqualTo("1.0");
        assertThat(constancia.getIpAddress()).isNotBlank();

        ResponseEntity<AdolescentProfileResponse[]> lista = get(RUTA, ana, AdolescentProfileResponse[].class);
        assertThat(lista.getBody()).extracting(AdolescentProfileResponse::firstName).containsExactly("Sofi");
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("Sin autorización, sin escuchar al adolescente o fuera de los 13 a 17 no nace nada")
    void sinAutorizacionNoNace() {
        assertThat(post(RUTA, ana, cuerpo("Sofi", anoActual - 15, false, true), Map.class).getStatusCode().value())
                .isEqualTo(400);
        assertThat(post(RUTA, ana, cuerpo("Sofi", anoActual - 15, true, false), Map.class).getStatusCode().value())
                .isEqualTo(400);
        ResponseEntity<Map> nino = post(RUTA, ana, cuerpo("Nico", anoActual - 10, true, true), Map.class);
        assertThat(nino.getStatusCode().value()).isEqualTo(422);
        assertThat(nino.getBody().get("error").toString()).contains("13 a 17");
        assertThat(post(RUTA, ana, cuerpo("Juan", anoActual - 18, true, true), Map.class).getStatusCode().value())
                .isEqualTo(422);
        assertThat(profiles.count()).isZero();
        assertThat(authorizations.count()).isZero();
    }

    @Test
    @DisplayName("Editar cambia nombre, nivel y objetivo; revocar borra el perfil que no tiene clases")
    void editarYRevocar() {
        UUID id = post(RUTA, ana, cuerpo("Sofi", anoActual - 14, true, true), AdolescentProfileResponse.class)
                .getBody().id();

        ResponseEntity<AdolescentProfileResponse> editado = patch(RUTA + "/" + id, ana,
                Map.of("firstName", "Sofía", "selfDeclaredLevel", "INTERMEDIATE", "motivation", "Un intercambio"),
                AdolescentProfileResponse.class);
        assertThat(editado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(editado.getBody().firstName()).isEqualTo("Sofía");
        assertThat(editado.getBody().selfDeclaredLevel()).isEqualTo("INTERMEDIATE");

        assertThat(delete(RUTA + "/" + id, ana, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(profiles.findById(id)).isEmpty();
        assertThat(authorizations.findAll()).isEmpty();
        assertThat(get(RUTA, ana, List.class).getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("Solo la cuenta de un estudiante tiene adolescentes a su cargo, y nadie ve los de otro")
    void soloElAdultoYSoloLosSuyos() {
        assertThat(post(RUTA, maria, cuerpo("Sofi", anoActual - 15, true, true), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        UUID id = post(RUTA, ana, cuerpo("Sofi", anoActual - 15, true, true), AdolescentProfileResponse.class)
                .getBody().id();
        createUser("carlos@orion.test", "Carlos Díaz", UserRole.STUDENT);
        Session carlos = login("carlos@orion.test");
        assertThat(patch(RUTA + "/" + id, carlos, Map.of("firstName", "Otro"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(RUTA, carlos, List.class).getBody()).isEmpty();
    }
}
