package co.orion.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PayoutCutJob;
import co.orion.billing.application.PayoutHolds;
import co.orion.billing.application.PayoutService;
import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutLine;
import co.orion.billing.domain.PayoutLineKind;
import co.orion.billing.domain.PayoutStatus;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * El corte quincenal completo, con el reloj movido de corte en corte (brief de liquidaciones, paso 3):
 * qué entra y qué espera, que correr de más no duplica, la cancelación tardía, regenerar un borrador,
 * y la devolución de una clase ya pagada, que se descuenta en la siguiente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, LiquidacionesIT.RelojMovible.class})
class LiquidacionesIT extends ApiIntegrationSupport {

    @TestConfiguration
    static class RelojMovible {
        static final AtomicReference<Instant> AHORA = new AtomicReference<>(Instant.parse("2026-10-10T15:00:00Z"));

        @Bean
        @Primary
        Clock relojMovible() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return Clock.fixed(AHORA.get(), zone);
                }

                @Override
                public Instant instant() {
                    return AHORA.get();
                }
            };
        }

        /** Sin Cloudinary: guarda la clave y firma una URL de mentira. */
        @Bean
        @Primary
        co.orion.identity.application.DocumentStorage almacenFalso() {
            return new co.orion.identity.application.DocumentStorage() {
                @Override
                public String upload(byte[] bytes, String contentType, UUID userId, String fileName) {
                    return "orion/documents/" + userId + "/" + fileName;
                }

                @Override
                public String signedUrl(String storageKey, String contentType, Duration ttl) {
                    return "https://firmada.test/" + storageKey;
                }
            };
        }
    }

    @Autowired
    private PayoutCutJob corte;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @Autowired
    private PayoutService payouts;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private User admin;

    @BeforeEach
    void sembrar() {
        // Correos propios: esta prueba no borra tablas globales. Los pagos y liquidaciones de otras
        // pruebas ya los limpia ApiIntegrationSupport, así que el corte solo ve lo que siembra esta.
        String sufijo = UUID.randomUUID().toString();
        ana = createUser("ana." + sufijo + "@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria." + sufijo + "@orion.test", "María Gómez", UserRole.PROFESSOR);
        admin = createUser("admin." + sufijo + "@orion.test", "Orion Admin", UserRole.ADMIN);
        approveTeacher(maria.getId());
        mover(2026, 10, 10, 10, 0);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
    }

    @Test
    void elCorteLiquidaLoQueYaPasoSuPlazoYNoSeDuplica() {
        UUID dictada = clase(2026, 10, 5, 18, "COMPLETED", 50_000, 2000);
        UUID tardia = clase(2026, 10, 8, 9, "CANCELLED_BY_STUDENT", 50_000, 2000);
        UUID enPlazo = clase(2026, 10, 15, 21, "COMPLETED", 50_000, 1500);
        clase(2026, 10, 6, 10, "COMPLETED", 0, 2000);   // la prueba gratis: no genera línea

        // El 15 a las 23:00 la última quincena cerrada es la de septiembre: nada de octubre entra.
        mover(2026, 10, 15, 23, 0);
        assertThat(corte.run()).isZero();

        // Pasa el corte del 16: entran la dictada y la cancelación tardía; la del 15 sigue en plazo.
        mover(2026, 10, 16, 1, 0);
        assertThat(corte.run()).isEqualTo(1);
        Payout octubre = payouts.ofFortnight(LocalDate.of(2026, 10, 1)).getFirst();
        assertThat(payouts.linesOf(octubre.getId())).extracting(PayoutLine::getBookingId).containsExactlyInAnyOrder(dictada, tardia);
        assertThat(payouts.linesOf(octubre.getId())).extracting(PayoutLine::getKind)
                .containsExactlyInAnyOrder(PayoutLineKind.CLASS, PayoutLineKind.LATE_CANCELLATION);
        assertThat(octubre.getGrossCop()).isEqualTo(100_000);
        assertThat(octubre.getAmountCop()).isEqualTo(80_000);
        // Tercer día hábil desde el viernes 16: viernes, lunes 19 y martes 20.
        assertThat(octubre.getCommittedPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        // Sin acuerdo con el mandato, retenida y con su motivo.
        assertThat(octubre.getStatus()).isEqualTo(PayoutStatus.ON_HOLD);
        assertThat(octubre.getHoldReason()).isEqualTo(PayoutHolds.SIN_MANDATO);

        // Correr otra vez, una hora después, no crea nada.
        mover(2026, 10, 16, 2, 0);
        assertThat(corte.run()).isZero();
        assertThat(payouts.ofFortnight(LocalDate.of(2026, 10, 1))).hasSize(1);

        // El corte del 1 de noviembre recoge la que estaba en plazo, con su comisión de fundador.
        mover(2026, 11, 1, 1, 0);
        assertThat(corte.run()).isEqualTo(1);
        Payout segunda = payouts.ofFortnight(LocalDate.of(2026, 10, 16)).getFirst();
        assertThat(payouts.linesOf(segunda.getId())).singleElement().satisfies(l -> {
            assertThat(l.getBookingId()).isEqualTo(enPlazo);
            assertThat(l.getCommissionRateBps()).isEqualTo(1500);
            assertThat(l.getNetCop()).isEqualTo(42_500);
        });
    }

    /** Regenerar un borrador toma lo de hoy; una liquidación aprobada ya no se regenera. */
    @Test
    void regenerarUnBorradorYNadaMas() {
        listaParaCobrar();
        clase(2026, 10, 5, 18, "COMPLETED", 50_000, 2000);
        mover(2026, 10, 16, 1, 0);
        corte.run();
        Payout borrador = payouts.ofFortnight(LocalDate.of(2026, 10, 1)).getFirst();
        assertThat(borrador.getStatus()).isEqualTo(PayoutStatus.DRAFT);

        // Una clase cuyo reclamo se cerró después del corte (aquí: su pago se liberó después).
        UUID tarde = clase(2026, 10, 9, 18, "COMPLETED", 60_000, 2000);
        Payout rehecha = payouts.regenerate(borrador.getId(), admin.getId()).orElseThrow();
        assertThat(payouts.linesOf(rehecha.getId())).extracting(PayoutLine::getBookingId).contains(tarde);
        assertThat(rehecha.getAmountCop()).isEqualTo(40_000 + 48_000);

        payouts.approve(rehecha.getId(), admin.getId());
        assertThatThrownBy(() -> payouts.regenerate(rehecha.getId(), admin.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    /**
     * Una clase ya pagada que termina devuelta al estudiante se descuenta en la siguiente liquidación;
     * si el descuento se come todo, esa no se paga y el saldo se arrastra.
     */
    @Test
    void unaDevolucionSobreAlgoYaPagadoSeDescuentaEnLaSiguiente() {
        listaParaCobrar();
        UUID dictada = clase(2026, 10, 5, 18, "COMPLETED", 50_000, 2000);
        mover(2026, 10, 16, 1, 0);
        corte.run();
        Payout primera = payouts.ofFortnight(LocalDate.of(2026, 10, 1)).getFirst();
        payouts.approve(primera.getId(), admin.getId());
        payouts.pay(primera.getId(), LocalDate.of(2026, 10, 16), "BREB-1", true, admin.getId());

        payouts.onRefunded(dictada);
        payouts.onRefunded(dictada);   // dos veces el mismo evento: un solo ajuste
        assertThat(jdbc.queryForObject("select count(*) from payout_adjustments where booking_id = ?", Integer.class, dictada))
                .isEqualTo(1);

        // En la siguiente quincena solo hay una clase de $30.000 (neto $24.000): el ajuste de −$40.000 la supera.
        clase(2026, 10, 20, 18, "COMPLETED", 30_000, 2000);
        mover(2026, 11, 1, 1, 0);
        corte.run();
        Payout segunda = payouts.ofFortnight(LocalDate.of(2026, 10, 16)).getFirst();
        assertThat(segunda.getAdjustmentsCop()).isEqualTo(-40_000);
        assertThat(segunda.getAmountCop()).isEqualTo(-16_000);
        assertThat(segunda.getStatus()).isEqualTo(PayoutStatus.CARRIED_OVER);
        assertThat(jdbc.queryForObject("select amount_cop from payout_adjustments where kind = 'CARRY_OVER' and applied_payout_id is null",
                Long.class)).isEqualTo(-16_000);
        // Y la pagada no se tocó.
        assertThat(payouts.get(primera.getId()).getAmountCop()).isEqualTo(40_000);
        assertThat(payouts.get(primera.getId()).getStatus()).isEqualTo(PayoutStatus.PAID);
    }

    /**
     * Lo que ve el profe (paso 5): el próximo corte, sus clases por liquidar con el motivo en palabras,
     * el historial y el comprobante. El de otro profe no existe para él; un estudiante no tiene puerta.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void elProfeVeSusLiquidacionesYSuComprobante() throws Exception {
        listaParaCobrar();
        UUID dictada = clase(2026, 10, 5, 18, "COMPLETED", 50_000, 2000);
        clase(2026, 10, 15, 21, "COMPLETED", 50_000, 2000);
        mover(2026, 10, 16, 1, 0);
        corte.run();
        Payout liquidacion = payouts.ofFortnight(LocalDate.of(2026, 10, 1)).getFirst();
        payouts.approve(liquidacion.getId(), admin.getId());
        payouts.pay(liquidacion.getId(), LocalDate.of(2026, 10, 16), "BREB-7788", true, admin.getId());

        Session sesion = login(maria.getEmail());
        Map mias = get("/api/v1/me/payouts", sesion, Map.class).getBody();
        assertThat(mias.get("explanation")).isEqualTo(
                "Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión.");
        assertThat(mias.get("nextPeriodStart")).isEqualTo("2026-10-16");
        assertThat((List<Map>) mias.get("pending")).singleElement()
                .satisfies(p -> assertThat(p.get("reason")).isEqualTo("Entra en el corte del 1 de noviembre"));
        assertThat((List<Map>) mias.get("payouts")).singleElement()
                .satisfies(p -> assertThat(p).containsEntry("status", "PAID").containsEntry("paidOn", "2026-10-16"));

        Map comprobante = get("/api/v1/me/payouts/" + liquidacion.getId() + "/receipt", sesion, Map.class).getBody();
        assertThat(comprobante).containsEntry("professorName", "María Gómez").containsEntry("reference", "BREB-7788")
                .containsEntry("payeeMaskedKey", "••••4567").containsEntry("professorDocument", "CC ••••050");
        assertThat(((Number) comprobante.get("netCop")).longValue()).isEqualTo(40_000);
        assertThat((List<Map>) comprobante.get("lines")).singleElement()
                .satisfies(l -> assertThat(l.get("studentLabel")).isEqualTo("Ana R."));
        // El mandatario: sin ajuste, el responsable de los datos legales.
        assertThat(comprobante.get("mandataryName")).isNotNull();
        assertThat(dictada).isNotNull();

        // Otro profe: como si no existiera. Un estudiante: sin puerta. El admin: el mismo comprobante.
        User juan = createUser("juan." + UUID.randomUUID() + "@orion.test", "Juan Torres", UserRole.PROFESSOR);
        assertThat(get("/api/v1/me/payouts/" + liquidacion.getId() + "/receipt", login(juan.getEmail()), Map.class)
                .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/me/payouts/" + liquidacion.getId() + "/receipt", login(ana.getEmail()), Map.class)
                .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/payouts/" + liquidacion.getId() + "/receipt", login(admin.getEmail()), Map.class)
                .getBody()).containsEntry("reference", "BREB-7788");

        // Y le llega por correo, con el comprobante en el cuerpo.
        org.mockito.ArgumentCaptor<jakarta.mail.internet.MimeMessage> enviados =
                org.mockito.ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.timeout(5000).atLeastOnce()).send(enviados.capture());
        jakarta.mail.internet.MimeMessage pago = enviados.getAllValues().stream()
                .filter(m -> {
                    try {
                        return m.getSubject().startsWith("Te pagamos");
                    } catch (jakarta.mail.MessagingException e) {
                        return false;
                    }
                }).findFirst().orElseThrow();
        var out = new java.io.ByteArrayOutputStream();
        pago.writeTo(out);
        String cuerpo = out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("=\r\n", "");
        assertThat(cuerpo).contains("BREB-7788").contains("4567").doesNotContain("3001234567").contains("/comprobante/");
    }

    /**
     * Las exportaciones cuadran con las liquidaciones (paso 6): lo entregado es lo pagado, y la clase
     * de diciembre que se liquida en el corte de enero aparece como pendiente al 31 de diciembre.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void lasExportacionesCuadranConLasLiquidaciones() {
        listaParaCobrar();
        clase(2026, 10, 5, 18, "COMPLETED", 50_000, 2000);      // se liquida y se paga en octubre
        clase(2026, 12, 30, 18, "COMPLETED", 60_000, 1500);     // pendiente al cierre del año
        mover(2026, 10, 16, 1, 0);
        corte.run();
        Payout pagada = payouts.ofFortnight(LocalDate.of(2026, 10, 1)).getFirst();
        payouts.approve(pagada.getId(), admin.getId());
        payouts.pay(pagada.getId(), LocalDate.of(2026, 10, 16), "BREB-1", true, admin.getId());

        Session sesionAdmin = login(admin.getEmail());
        String anual = get("/api/v1/admin/payouts/reports/annual.csv?year=2026", sesionAdmin, String.class).getBody();
        // Recibido 110.000; comisión 10.000 + 9.000; entregado 40.000; pendiente 51.000 (la de diciembre).
        assertThat(anual).startsWith("\uFEFFprofe_documento,profe_nombre,recibido_en_su_nombre_cop")
                .contains("\"CC 1020304050\",\"María Gómez\",110000,19000,40000,51000,0");

        String libro = get("/api/v1/admin/payouts/reports/ledger.csv?from=2026-10-01&to=2026-12-31", sesionAdmin,
                String.class).getBody();
        assertThat(libro.lines()).hasSize(3);
        assertThat(libro).contains("2026-10-01 a 2026-10-15,PAID,2026-10-16");

        String comisiones = get("/api/v1/admin/payouts/reports/commissions.csv?year=2026", sesionAdmin, String.class).getBody();
        assertThat(comisiones).contains("2026-10,1,50000,10000").contains("2026-12,1,60000,9000");

        Map resumen = get("/api/v1/admin/payouts/year-summary?year=2026", sesionAdmin, Map.class).getBody();
        assertThat(((Number) resumen.get("commissionCop")).longValue()).isEqualTo(19_000);
        assertThat(((Number) resumen.get("collectedCop")).longValue()).isEqualTo(110_000);
        assertThat(((Number) resumen.get("referenceUvt")).intValue()).isEqualTo(3500);

        Map borrador = get("/api/v1/admin/payouts/certificates/" + maria.getId() + "/2026/draft", sesionAdmin, Map.class).getBody();
        assertThat(borrador).containsEntry("professorName", "María Gómez").containsEntry("withheldCop", 0);
        assertThat(((Number) borrador.get("deliveredCop")).longValue()).isEqualTo(40_000);
    }

    /** El certificado firmado: solo lo ven el profe dueño y el admin, y mientras no se sube, no existe. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void elCertificadoSoloLoVenElProfeYElAdmin() {
        Session sesionAdmin = login(admin.getEmail());
        Session sesionMaria = login(maria.getEmail());
        assertThat(get("/api/v1/me/payouts/certificates", sesionMaria, List.class).getBody()).isEmpty();
        assertThat(get("/api/v1/me/payouts/certificates/2026/url", sesionMaria, Map.class).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);

        org.springframework.util.LinkedMultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.http.HttpHeaders deLaParte = new org.springframework.http.HttpHeaders();
        deLaParte.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        form.add("file", new org.springframework.http.HttpEntity<>(new org.springframework.core.io.ByteArrayResource(
                "%PDF-1.4 firmado".getBytes()) {
            @Override
            public String getFilename() {
                return "certificado.pdf";
            }
        }, deLaParte));
        org.springframework.http.HttpHeaders cabeceras = new org.springframework.http.HttpHeaders();
        cabeceras.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        cabeceras.add(org.springframework.http.HttpHeaders.COOKIE,
                "ORION_SESSION=" + sesionAdmin.cookie() + "; XSRF-TOKEN=" + sesionAdmin.csrfToken());
        cabeceras.add("X-XSRF-TOKEN", sesionAdmin.csrfToken());
        assertThat(rest.postForEntity("/api/v1/admin/payouts/certificates/" + maria.getId() + "/2026",
                new org.springframework.http.HttpEntity<>(form, cabeceras), Map.class).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.OK);

        assertThat(get("/api/v1/me/payouts/certificates", sesionMaria, List.class).getBody()).hasSize(1);
        assertThat(get("/api/v1/me/payouts/certificates/2026/url", sesionMaria, Map.class).getBody().get("url"))
                .asString().startsWith("https://firmada.test/");
        // Otro profe no ve el de María: el suyo no existe. Un estudiante no tiene puerta.
        User juan = createUser("juan." + UUID.randomUUID() + "@orion.test", "Juan Torres", UserRole.PROFESSOR);
        assertThat(get("/api/v1/me/payouts/certificates/2026/url", login(juan.getEmail()), Map.class).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/me/payouts/certificates", login(ana.getEmail()), String.class).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/payouts/certificates/" + maria.getId() + "/2026/url", sesionAdmin, Map.class)
                .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
    }

    /** Una clase dictada con su pago liberado, un día a una hora de Bogotá. Devuelve la reserva. */
    private UUID clase(int anio, int mes, int dia, int hora, String estado, long bruto, int bps) {
        Instant inicio = LocalDateTime.of(anio, mes, dia, hora, 0).atZone(BusinessZone.BOGOTA).toInstant();
        Booking b = bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(), inicio,
                inicio.plus(Duration.ofMinutes(55)), BookingModality.VIRTUAL, null, ana.getId()));
        jdbc.update("update bookings set status = ?, completed_at = ends_at where id = ?", estado, b.getId());
        long comision = bruto * bps / 10_000;
        jdbc.update("""
                insert into payments (booking_id, student_id, professor_id, amount_cop, credit_applied_cop, charged_cop,
                                      commission_rate_bps, commission_cop, professor_earnings_cop, status, paid_at, released_at)
                values (?, ?, ?, ?, 0, ?, ?, ?, ?, 'RELEASED', ?, ?)
                """, b.getId(), ana.getId(), maria.getId(), bruto, bruto, bps, comision, bruto - comision,
                Timestamp.from(inicio), Timestamp.from(inicio.plus(Duration.ofHours(1))));
        return b.getId();
    }

    private void listaParaCobrar() {
        Session sesion = login(maria.getEmail());
        post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class);
        put("/api/v1/me/payout-details", sesion, Map.of("keyType", "PHONE", "key", "3001234567",
                "documentType", "CC", "documentNumber", "1020304050", "holderName", "María Gómez"), Map.class);
    }

    private static void mover(int anio, int mes, int dia, int hora, int minuto) {
        RelojMovible.AHORA.set(LocalDateTime.of(anio, mes, dia, hora, minuto).atZone(BusinessZone.BOGOTA).toInstant());
    }
}
