package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.application.RoomPresence;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El webhook de JaaS, de la firma a lo que ve cada quien.
 *
 * <p>Lo que se prueba es lo que lo hace confiable: sin firma válida no entra nada; un evento
 * reenviado cuenta una vez; lo que no es de nuestra app, de una reserva nuestra o de uno de sus dos
 * participantes se ignora; los eventos desordenados no dejan a nadie «dentro» por error; y el
 * tiempo hablado lo ve el profesor de esa clase y el admin, nadie más.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "orion.jaas.app-id=vpaas-magic-cookie-prueba",
        "orion.jaas.webhook-secret=whsec_secreto_de_prueba"})
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ClassroomWebhookIT extends ApiIntegrationSupport {

    private static final String APP = "vpaas-magic-cookie-prueba";
    private static final String SECRETO = "whsec_secreto_de_prueba";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private RoomPresence presencia;

    private User maria;
    private User ana;
    private Booking clase;

    @BeforeEach
    void seed() {
        jdbc.update("delete from room_participations");
        jdbc.update("delete from video_webhook_events");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        Instant inicio = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.HOURS);
        clase = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(), inicio,
                BookingModality.VIRTUAL, null, ana.getId()));
    }

    private ResponseEntity<String> enviar(String cuerpo, String secreto) {
        long t = Instant.now().getEpochSecond();
        String firma = Base64.getEncoder().encodeToString(hmac(secreto, t + "." + cuerpo));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Jaas-Signature", "t=" + t + ",v1=" + firma);
        return rest.postForEntity("/api/v1/webhooks/video/jaas", new HttpEntity<>(cuerpo, h), String.class);
    }

    /** Firmado como firma 8x8, con el secreto entero: el caso que el verificador acepta. */
    private static byte[] hmac(String llave, String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String evento(String tipo, String llave, String app, UUID sala, long ms, String data) {
        return """
                {"idempotencyKey":"%s","appId":"%s","eventType":"%s","timestamp":%d,
                 "fqn":"%s/%s","data":%s}""".formatted(llave, app, tipo, ms, app, sala, data);
    }

    private String entra(String llave, UUID quien, long ms) {
        return evento("PARTICIPANT_JOINED", llave, APP, clase.getId(), ms,
                "{\"id\":\"%s\",\"name\":\"x\",\"moderator\":\"true\"}".formatted(quien));
    }

    private String sale(String llave, UUID quien, long ms) {
        return evento("PARTICIPANT_LEFT", llave, APP, clase.getId(), ms,
                "{\"id\":\"%s\",\"name\":\"x\"}".formatted(quien));
    }

    private String hablaron(String llave, long msAna, long msMaria) {
        return evento("SPEAKER_STATS", llave, APP, clase.getId(), System.currentTimeMillis(), """
                {"a@8x8.vc/1":{"time":%d,"id":"%s"},"b@8x8.vc/2":{"time":%d,"id":"%s"}}"""
                .formatted(msAna, ana.getId(), msMaria, maria.getId()));
    }

    @Test
    @DisplayName("Sin firma válida no entra nada")
    void sinFirmaNoEntra() {
        ResponseEntity<String> r = enviar(entra("k1", maria.getId(), System.currentTimeMillis()), "whsec_otro");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(presencia.estaDentro(clase.getId(), maria.getId())).isFalse();
    }

    @Test
    @DisplayName("Cuando el profesor entra, la antesala del estudiante lo sabe; cuando sale, también")
    void laAntesalaSeEntera() {
        long ahora = System.currentTimeMillis();
        assertThat(enviar(entra("k1", maria.getId(), ahora), SECRETO).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(presencia.estaDentro(clase.getId(), maria.getId())).isTrue();

        enviar(sale("k2", maria.getId(), ahora + 60_000), SECRETO);
        assertThat(presencia.estaDentro(clase.getId(), maria.getId())).isFalse();
    }

    @Test
    @DisplayName("Desordenados: una salida más vieja que la entrada no deja a nadie fuera")
    void desordenados() {
        long ahora = System.currentTimeMillis();
        enviar(entra("k1", maria.getId(), ahora), SECRETO);
        enviar(sale("k2", maria.getId(), ahora - 60_000), SECRETO);

        assertThat(presencia.estaDentro(clase.getId(), maria.getId())).isTrue();
    }

    @Test
    @DisplayName("Un evento reenviado cuenta una vez: el tiempo hablado no se duplica")
    void reenvioCuentaUnaVez() {
        enviar(hablaron("s1", 600_000, 900_000), SECRETO);
        enviar(hablaron("s1", 600_000, 900_000), SECRETO);
        // Una segunda sesión en la misma sala sí se suma.
        enviar(hablaron("s2", 60_000, 0), SECRETO);

        assertThat(jdbc.queryForObject(
                "select speaking_ms from room_participations where booking_id = ? and user_id = ?",
                Integer.class, clase.getId(), ana.getId())).isEqualTo(660_000);
    }

    @Test
    @DisplayName("Lo ajeno se ignora: otra app, una sala que no es reserva, alguien que no es de la clase")
    void loAjenoSeIgnora() {
        long ahora = System.currentTimeMillis();
        enviar(evento("PARTICIPANT_JOINED", "x1", "vpaas-otra-app", clase.getId(), ahora,
                "{\"id\":\"%s\"}".formatted(maria.getId())), SECRETO);
        enviar(evento("PARTICIPANT_JOINED", "x2", APP, UUID.randomUUID(), ahora,
                "{\"id\":\"%s\"}".formatted(maria.getId())), SECRETO);
        enviar(entra("x3", UUID.randomUUID(), ahora), SECRETO);

        assertThat(jdbc.queryForObject("select count(*) from room_participations", Integer.class)).isZero();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El tiempo hablado lo ve el profesor de esa clase y el admin; otro profesor, no")
    void quienVeElTiempoHablado() {
        enviar(entra("k1", maria.getId(), clase.getStartsAt().plus(Duration.ofMinutes(8)).toEpochMilli()), SECRETO);
        enviar(hablaron("s1", 1_200_000, 1_800_000), SECRETO);
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Admin", UserRole.ADMIN);

        List<Map> deMaria = get("/api/v1/professors/me/students/" + ana.getId() + "/classroom",
                login("maria@orion.test"), List.class).getBody();
        assertThat(deMaria).hasSize(1);
        assertThat(deMaria.getFirst().get("studentSpeakingMs")).isEqualTo(1_200_000);

        assertThat(get("/api/v1/professors/me/students/" + ana.getId() + "/classroom",
                login("juan@orion.test"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/professors/me/students/" + ana.getId() + "/classroom",
                login("ana@orion.test"), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        List<Map> admin = get("/api/v1/admin/classroom-stats", login("admin@orion.test"), List.class).getBody();
        assertThat(admin).hasSize(1);
        Map fila = admin.getFirst();
        assertThat(((Number) fila.get("parteDelEstudiante")).doubleValue()).isEqualTo(0.4);
        assertThat(fila.get("llegadasTarde")).isEqualTo(1);
    }
}
