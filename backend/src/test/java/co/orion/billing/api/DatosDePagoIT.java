package co.orion.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PayoutHolds;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;

/**
 * Los datos de pago del profe (brief de liquidaciones, paso 2): los escribe él, los ve enmascarados,
 * nadie más los lee por esta puerta, y cada cambio le llega por correo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class DatosDePagoIT extends ApiIntegrationSupport {

    private static final String URL = "/api/v1/me/payout-details";

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PayoutHolds holds;

    private final List<UUID> creados = new ArrayList<>();

    @BeforeEach
    void correo() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    @AfterEach
    void limpiar() {
        for (UUID id : creados) {
            jdbc.update("delete from professor_payout_details where professor_id = ?", id);
            jdbc.update("delete from agreement_acceptances where user_id = ?", id);
            jdbc.update("delete from teacher_applications where user_id = ?", id);
            jdbc.update("delete from student_profiles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void elProfeLosRegistraYLosVeEnmascarados() throws Exception {
        User maria = usuario(UserRole.PROFESSOR);
        Session sesion = login(maria.getEmail());
        assertThat(get(URL, sesion, Map.class).getBody()).containsEntry("details", null);

        ResponseEntity<String> guardado = put(URL, sesion, datos("300 123 4567"), String.class);

        assertThat(guardado.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Ni en la respuesta de guardar aparece el dato completo.
        assertThat(guardado.getBody()).doesNotContain("3001234567").doesNotContain("1020304050");
        Map<String, Object> vista = (Map<String, Object>) get(URL, sesion, Map.class).getBody().get("details");
        assertThat(vista).containsEntry("keyType", "PHONE").containsEntry("maskedKey", "••••4567")
                .containsEntry("maskedDocument", "••••050").containsEntry("holderName", "María Gómez");
        // Guardado completo y normalizado, para el admin.
        assertThat(jdbc.queryForObject("select key_value from professor_payout_details where professor_id = ?",
                String.class, maria.getId())).isEqualTo("3001234567");
    }

    /** Cada cambio le llega al profe por correo: si no fue él, alguien quiere desviar sus pagos. */
    @Test
    void cadaCambioLeLlegaPorCorreo() throws Exception {
        User maria = usuario(UserRole.PROFESSOR);
        Session sesion = login(maria.getEmail());

        put(URL, sesion, datos("3001234567"), Map.class);
        verify(mailSender, timeout(5000).times(1)).send(any(MimeMessage.class));
        put(URL, sesion, datos("3109876543"), Map.class);

        ArgumentCaptor<MimeMessage> enviados = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(enviados.capture());
        assertThat(enviados.getAllValues().get(0).getSubject()).isEqualTo("Registraste tus datos de pago en Orión");
        MimeMessage cambio = enviados.getAllValues().get(1);
        assertThat(cambio.getSubject()).isEqualTo("Cambiaron tus datos de pago en Orión");
        String cuerpo = texto(cambio);
        assertThat(cuerpo).contains("6543").doesNotContain("3109876543").contains("Si no fuiste t");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void unaLlaveQueNoSirveSeRechazaDiciendoPorQue() {
        User maria = usuario(UserRole.PROFESSOR);

        ResponseEntity<Map> r = put(URL, login(maria.getEmail()), datos("6012345678"), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) r.getBody().get("error")).contains("10 dígitos de tu celular");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void soloElProfeTienePuerta() {
        User ana = usuario(UserRole.STUDENT);
        Session estudiante = login(ana.getEmail());

        assertThat(get(URL, estudiante, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(URL, estudiante, datos("3001234567"), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.getForEntity(URL, Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Sin datos de pago, la liquidación queda retenida con ese motivo; con ellos, se puede pagar. */
    @Test
    void sinDatosLaLiquidacionQuedaRetenida() {
        User maria = usuario(UserRole.PROFESSOR);
        Session sesion = login(maria.getEmail());
        post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class);

        assertThat(holds.motivo(maria.getId())).contains(PayoutHolds.SIN_DATOS_DE_PAGO);
        put(URL, sesion, datos("3001234567"), Map.class);
        assertThat(holds.motivo(maria.getId())).isEmpty();
    }

    private User usuario(UserRole rol) {
        User u = createUser("u." + UUID.randomUUID() + "@orion.test", "María Gómez", rol);
        creados.add(u.getId());
        if (rol == UserRole.PROFESSOR) {
            approveTeacher(u.getId());
        }
        return u;
    }

    private static Map<String, String> datos(String celular) {
        return Map.of("keyType", "PHONE", "key", celular, "documentType", "CC",
                "documentNumber", "1.020.304.050", "holderName", "María Gómez");
    }

    private static String texto(MimeMessage m) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        m.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("=\r\n", "");
    }
}
