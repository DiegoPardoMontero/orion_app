package co.orion.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.messaging.application.NotificationService;
import co.orion.notifications.application.PushGateway;
import co.orion.support.ApiIntegrationSupport;

/**
 * Avisos en el dispositivo: suscribirse solo hacia servicios de push conocidos, que suene lo que
 * debe sonar (y no lo demás), y que una suscripción muerta se borre sola.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class PushIT extends ApiIntegrationSupport {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc123";

    @DynamicPropertySource
    static void clavesVapid(DynamicPropertyRegistry registro) throws Exception {
        KeyPair par = parP256();
        registro.add("orion.push.vapid.public-key", () -> B64.encodeToString(sinComprimir(par)));
        registro.add("orion.push.vapid.private-key", () -> B64.encodeToString(escalar(par)));
    }

    @MockitoBean
    private PushGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NotificationService notifications;

    private User ana;
    private Session anaSession;
    private Map<String, Object> suscripcion;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("delete from push_subscriptions");
        users.deleteAll();
        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        anaSession = login("ana@orion.test");

        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        suscripcion = Map.of("endpoint", FCM, "keys", Map.of(
                "p256dh", B64.encodeToString(sinComprimir(parP256())), "auth", B64.encodeToString(auth)));
        when(gateway.enviar(any(), any(), anyMap())).thenReturn(201);
    }

    private int suscripciones() {
        return jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("La configuración trae la clave pública y suscribirse guarda este navegador")
    void suscribirse() {
        Map config = get("/api/v1/push/config", anaSession, Map.class).getBody();
        assertThat(config).containsEntry("enabled", true);
        assertThat((String) config.get("publicKey")).isNotBlank();

        assertThat(post("/api/v1/me/push-subscriptions", anaSession, suscripcion, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/api/v1/me/push-subscriptions", anaSession, suscripcion, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(suscripciones()).isEqualTo(1);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una dirección que no es de un servicio de push no se acepta: Orión no llama a donde le digan")
    void soloServiciosConocidos() {
        for (String malo : new String[] {"http://fcm.googleapis.com/x", "https://localhost:8080/actuator",
                "https://169.254.169.254/latest", "https://fcm.googleapis.com.evil.test/x",
                "https://evilpush.apple.com/x"}) {
            var cuerpo = Map.of("endpoint", malo, "keys", suscripcion.get("keys"));
            assertThat(post("/api/v1/me/push-subscriptions", anaSession, cuerpo, Map.class).getStatusCode())
                    .as(malo).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        }
        assertThat(suscripciones()).isZero();
    }

    @Test
    @DisplayName("Suena lo que pide hacer algo pronto; un logro se queda en la campana")
    void suenaLoQueDebe() {
        post("/api/v1/me/push-subscriptions", anaSession, suscripcion, Void.class);

        notifications.create(ana.getId(), "ACHIEVEMENT_UNLOCKED", "Encendiste una estrella", "Primer mensaje", "/logros");
        notifications.create(ana.getId(), "CLASS_SOON", "En una hora: tu clase con María", "Empieza a las 7:00 PM.",
                "/mis-clases/x/aula");

        verify(gateway, timeout(5000).times(1)).enviar(eq(FCM), any(), anyMap());
    }

    @Test
    @DisplayName("Una suscripción que el servicio da por muerta (410) se borra")
    void laMuertaSeBorra() {
        post("/api/v1/me/push-subscriptions", anaSession, suscripcion, Void.class);
        when(gateway.enviar(any(), any(), anyMap())).thenReturn(410);

        @SuppressWarnings("unchecked")
        Map<String, Object> prueba = post("/api/v1/me/push-subscriptions/test", anaSession, null, Map.class).getBody();

        assertThat(prueba).containsEntry("devices", 0);
        assertThat(suscripciones()).isZero();
    }

    @Test
    @DisplayName("Apagar solo borra la propia; nadie apaga los avisos de otro con su endpoint")
    void apagar() {
        post("/api/v1/me/push-subscriptions", anaSession, suscripcion, Void.class);
        Session maria = login("maria@orion.test");

        post("/api/v1/me/push-subscriptions/remove", maria, Map.of("endpoint", FCM), Void.class);
        assertThat(suscripciones()).isEqualTo(1);

        post("/api/v1/me/push-subscriptions/remove", anaSession, Map.of("endpoint", FCM), Void.class);
        assertThat(suscripciones()).isZero();
        verify(gateway, never()).enviar(any(), any(), anyMap());
    }

    private static KeyPair parP256() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    private static byte[] sinComprimir(KeyPair par) {
        ECPublicKey p = (ECPublicKey) par.getPublic();
        byte[] r = new byte[65];
        r[0] = 0x04;
        fijo(p.getW().getAffineX().toByteArray(), r, 1);
        fijo(p.getW().getAffineY().toByteArray(), r, 33);
        return r;
    }

    private static byte[] escalar(KeyPair par) {
        byte[] r = new byte[32];
        fijo(((ECPrivateKey) par.getPrivate()).getS().toByteArray(), r, 0);
        return r;
    }

    private static void fijo(byte[] n, byte[] destino, int desde) {
        int largo = Math.min(32, n.length);
        System.arraycopy(n, n.length - largo, destino, desde + 32 - largo, largo);
    }
}
