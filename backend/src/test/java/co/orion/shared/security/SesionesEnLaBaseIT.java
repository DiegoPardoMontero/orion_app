package co.orion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.api.ChangePasswordRequest;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * Las sesiones viven en Postgres (V68) y no en la memoria del proceso: un despliegue ya no echa a
 * nadie. Lo que un despliegue hace es arrancar un proceso nuevo sobre la misma base; aquí ese proceso
 * nuevo es un repositorio de sesiones recién creado, sin nada en memoria, que tiene que encontrarla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SesionesEnLaBaseIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transacciones;

    /** Un correo nuevo en cada prueba: no depende de lo que otras pruebas dejen en la base. */
    private String ana;

    @BeforeEach
    void seed() {
        ana = "ana." + UUID.randomUUID() + "@orion.test";
        createUser(ana, "Ana Ramírez", UserRole.STUDENT);
    }

    private int sesionesDeAna() {
        return jdbc.queryForObject("select count(*) from spring_session where principal_name = ?", Integer.class, ana);
    }

    private byte[] contextoGuardadoDe(Session sesion) {
        return jdbc.queryForObject("""
                select a.attribute_bytes from spring_session_attributes a
                  join spring_session s on s.primary_id = a.session_primary_id
                 where s.session_id = ? and a.attribute_name = 'SPRING_SECURITY_CONTEXT'
                """, byte[].class, idDe(sesion));
    }

    /** La cookie lleva el id de la sesión en base64 (el serializador de Spring Session). */
    private static String idDe(Session sesion) {
        return new String(Base64.getDecoder().decode(sesion.cookie()), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("La sesión es una fila en Postgres, y un proceso nuevo —el de un despliegue— la lee entera")
    void sobreviveAUnDespliegue() {
        Session sesion = login(ana);
        assertThat(sesionesDeAna()).isEqualTo(1);

        JdbcIndexedSessionRepository procesoNuevo = new JdbcIndexedSessionRepository(
                new JdbcTemplate(dataSource), new TransactionTemplate(transacciones));
        procesoNuevo.setConversionService(SesionesEnLaBase.conversiones(getClass().getClassLoader()));
        org.springframework.session.Session guardada = procesoNuevo.findById(idDe(sesion));

        assertThat(guardada).isNotNull();
        SecurityContext contexto = guardada.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(contexto.getAuthentication().getName()).isEqualTo(ana);
        assertThat(get("/api/v1/auth/me", sesion, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("La base es la que manda: sin la fila no hay sesión, aunque el proceso siga vivo")
    void laBaseEsLaFuente() {
        Session sesion = login(ana);
        jdbc.update("delete from spring_session where session_id = ?", idDe(sesion));

        assertThat(get("/api/v1/auth/me", sesion, Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Salir borra la fila, y cambiar la contraseña borra la de las otras sesiones")
    void salirYCambiarLaContrasenaLaBorran() {
        Session sesion = login(ana);
        post("/api/v1/auth/logout", sesion, null, Void.class);
        assertThat(sesionesDeAna()).isZero();

        Session propia = login(ana);
        Session intruso = login(ana);
        post("/api/v1/me/password", propia, new ChangePasswordRequest(PASSWORD, "otra-clave-9"), Void.class);
        assertThat(get("/api/v1/auth/me", intruso, Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class,
                idDe(intruso))).isZero();
    }

    @Test
    @DisplayName("En la fila no queda la contraseña con la que se entró")
    void laContrasenaNoSeGuarda() {
        byte[] contexto = contextoGuardadoDe(login(ana));

        assertThat(new String(contexto, StandardCharsets.ISO_8859_1)).contains(ana).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("Una sesión guardada que ya no se puede leer no es un 500: se vuelve a entrar")
    void laIlegibleEsUnaSesionVacia() {
        Session sesion = login(ana);
        jdbc.update("""
                update spring_session_attributes set attribute_bytes = ?
                 where attribute_name = 'SPRING_SECURITY_CONTEXT'
                   and session_primary_id = (select primary_id from spring_session where session_id = ?)
                """, new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5, 1, 2, 3}, idDe(sesion));

        assertThat(get("/api/v1/auth/me", sesion, Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(ana).cookie()).isNotBlank();
    }
}
