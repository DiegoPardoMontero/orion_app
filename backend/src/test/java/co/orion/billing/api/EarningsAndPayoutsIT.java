package co.orion.billing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PayoutService;
import co.orion.billing.domain.PayoutCalculator;
import co.orion.admin.api.DashboardResponse;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.api.RecordAttendanceRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * Del dinero retenido a la transferencia: la clase se dicta, la plata se libera, el admin genera la
 * liquidación y la marca pagada. Y quién puede ver qué, que en este bloque es media funcionalidad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, EarningsAndPayoutsIT.FrozenClockConfiguration.class})
class EarningsAndPayoutsIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final long RATE_COP = 60_000;
    private static final long COMMISSION_COP = 9_000;   // 15 % de 60 000
    private static final long EARNINGS_COP = 51_000;

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private AvailabilityRuleRepository rules;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private StudentCreditRepository credits;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PayoutService payoutService;

    private User ana;
    private User maria;
    private User juan;
    private Session anaSession;
    private Session mariaSession;
    private Session juanSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        // Este test afirma cifras exactas de comisión, así que fija la tasa en vez de heredarla.
        // La lección la dejó `booking_min_lead_hours`: un test que depende de un ajuste y no lo
        // fija se rompe el día que alguien cambia el valor por defecto, y por el motivo equivocado.
        jdbc.update("update platform_settings set value = '1500' where key = 'commission_rate_bps'");

        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        publish(maria);
        publish(juan);

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
        juanSession = login("juan@orion.test");
        adminSession = login("admin@orion.test");
    }

    private void publish(User professor) {
        ProfessorProfile profile = new ProfessorProfile(professor);
        profile.changeRate(RATE_COP);
        profile.publish();
        profiles.save(profile);
        approveTeacher(professor.getId());
        rules.save(new AvailabilityRule(professor.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));
    }

    @Test
    void moneyIsHeldUntilTheClassHappensAndOnlyThenBecomesPayable() {
        UUID bookingId = bookAndPay(maria, 9);

        EarningsResponse held = earnings(mariaSession);
        assertThat(held.heldCop()).isEqualTo(EARNINGS_COP);
        assertThat(held.payableCop()).isZero();

        recordAttendance(bookingId, mariaSession);

        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.RELEASED);
        EarningsResponse payable = earnings(mariaSession);
        assertThat(payable.heldCop()).isZero();
        assertThat(payable.payableCop()).isEqualTo(EARNINGS_COP);
        assertThat(payable.transferredCop()).isZero();
    }

    @Test
    void aProfessorSeesTheCommissionOfTheirOwnClassesAndNothingOfAnybodyElses() {
        bookAndPay(maria, 9);

        EarningsResponse mine = earnings(mariaSession);
        assertThat(mine.lines()).hasSize(1);
        assertThat(mine.lines().get(0).amountCop()).isEqualTo(RATE_COP);
        assertThat(mine.lines().get(0).commissionCop()).isEqualTo(COMMISSION_COP);
        assertThat(mine.lines().get(0).earningsCop()).isEqualTo(EARNINGS_COP);
        assertThat(mine.lines().get(0).studentName()).isEqualTo("Ana Ramírez");

        // Juan no ve un peso de las clases de María.
        EarningsResponse other = earnings(juanSession);
        assertThat(other.lines()).isEmpty();
        assertThat(other.totalCop()).isZero();
    }

    /** El estudiante compra una clase, no un servicio de intermediación: la comisión no es suya. */
    @Test
    void theStudentNeverSeesTheCommissionInAnyResponse() {
        UUID bookingId = bookAndPay(maria, 9);

        String history = get("/api/v1/me/payments", anaSession, String.class).getBody();
        String status = get(BOOKINGS + "/" + bookingId + "/payment", anaSession, String.class).getBody();

        assertThat(history).contains("amountCop").doesNotContain("commission");
        assertThat(status).doesNotContain("commission");

        // Y el endpoint de ganancias es del profesor: para la estudiante no existe.
        assertThat(get("/api/v1/me/earnings", anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * El corte quincenal crea la liquidación (brief de liquidaciones, paso 3); el admin la aprueba,
     * transfiere y registra el pago (paso 4). La clase de María se dictó el 13 de julio: el corte del
     * 16 ya la ve fuera del plazo de reclamo.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void elCorteCreaLaLiquidacionYElAdminLaApruebaLaPagaYLaExporta() {
        UUID bookingId = bookAndPay(maria, 9);
        recordAttendance(bookingId, mariaSession);
        listaParaCobrar(mariaSession);

        assertThat(payoutService.runCut(PayoutCalculator.closedBy(LocalDate.of(2026, 7, 16)))).isEqualTo(1);
        // El mismo corte otra vez no paga la clase dos veces.
        assertThat(payoutService.runCut(PayoutCalculator.closedBy(LocalDate.of(2026, 7, 16)))).isZero();

        Map quincena = get("/api/v1/admin/payouts?periodStart=2026-07-01", adminSession, Map.class).getBody();
        assertThat(((Number) quincena.get("toTransferCop")).longValue()).isEqualTo(EARNINGS_COP);
        Map fila = ((List<Map>) quincena.get("payouts")).getFirst();
        assertThat(fila).containsEntry("professorName", "María Gómez").containsEntry("status", "DRAFT")
                .containsEntry("committedPayDate", "2026-07-21");
        String payoutId = (String) fila.get("id");
        // La clase ya va en una liquidación: su línea no dice «por cobrar».
        assertThat(earnings(mariaSession).lines()).singleElement()
                .extracting(EarningsResponse.Line::status).isEqualTo("IN_TRANSIT");

        // Sin aprobar no se paga, y la llave completa tampoco se ve.
        assertThat(post("/api/v1/admin/payouts/" + payoutId + "/pay", adminSession,
                pago("BREB-99812", true), Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/admin/payouts/" + payoutId + "/payee", adminSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/api/v1/admin/payouts/" + payoutId + "/approve", adminSession, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/admin/payouts/" + payoutId + "/payee", adminSession, Map.class).getBody())
                .containsEntry("key", "3001234567").containsEntry("holderName", "María Gómez");

        // Sin referencia, o sin confirmar el titular, no se marca pagada.
        assertThat(post("/api/v1/admin/payouts/" + payoutId + "/pay", adminSession, pago("", true), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/v1/admin/payouts/" + payoutId + "/pay", adminSession, pago("BREB-99812", false), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map pagada = post("/api/v1/admin/payouts/" + payoutId + "/pay", adminSession, pago("BREB-99812", true),
                Map.class).getBody();
        assertThat((Map<String, Object>) pagada.get("payout")).containsEntry("status", "PAID")
                .containsEntry("reference", "BREB-99812").containsEntry("paidOn", "2026-07-13");
        assertThat(pagada).containsEntry("payeeMaskedKey", "••••4567");
        // Pagada es inmutable: ni se vuelve a pagar ni se regenera.
        assertThat(post("/api/v1/admin/payouts/" + payoutId + "/pay", adminSession, pago("OTRA", true), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/v1/admin/payouts/" + payoutId + "/regenerate", adminSession, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String csv = get("/api/v1/admin/payouts/" + payoutId + "/export", adminSession, String.class).getBody();
        assertThat(csv)
                .startsWith("\uFEFFfecha_clase,estudiante,concepto,bruto_cop,comision_pct,comision_cop,neto_cop")
                .contains("\"Ana R.\"").contains(",60000,15," + COMMISSION_COP + "," + EARNINGS_COP)
                .contains("TOTAL,,,60000,," + COMMISSION_COP + "," + EARNINGS_COP);

        // Y ya transferido, deja de estar "por cobrar" para el profesor.
        EarningsResponse after = earnings(mariaSession);
        assertThat(after.payableCop()).isZero();
        assertThat(after.transferredCop()).isEqualTo(EARNINGS_COP);
        assertThat(after.lines()).singleElement().extracting(EarningsResponse.Line::status).isEqualTo("TRANSFERRED");

        // Y se entera sin entrar a mirar (24/09/2026): «Te pagamos $ …» en su campana.
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
                "select count(*) from notifications where user_id = ? and type = 'PAYOUT_PAID'", Integer.class,
                maria.getId()) == 1);
        // La aprobación y el pago quedan en la auditoría del admin, y también quién vio la llave.
        assertThat(jdbc.queryForList("select action from admin_audit_log where entity_id = ?::uuid order by created_at",
                String.class, payoutId)).containsExactlyInAnyOrder("APPROVE_PAYOUT", "VIEW_PAYOUT_PAYEE", "PAY_PAYOUT");
    }

    /** Sin la llave Bre-B, la liquidación queda retenida, dice por qué, y se libera sola al registrarla. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void sinAcuerdoNiDatosQuedaRetenidaYSeLevantaSola() {
        UUID bookingId = bookAndPay(maria, 9);
        recordAttendance(bookingId, mariaSession);
        payoutService.runCut(PayoutCalculator.closedBy(LocalDate.of(2026, 7, 16)));

        Map fila = ((List<Map>) get("/api/v1/admin/payouts?periodStart=2026-07-01", adminSession, Map.class)
                .getBody().get("payouts")).getFirst();
        // Con el reloj en julio de 2026 el acuerdo 2.0 (el del mandato) todavía no rige, así que lo que
        // falta son los datos de pago. La retención por el mandato la prueba LiquidacionesIT, en octubre.
        assertThat(fila).containsEntry("status", "ON_HOLD").containsEntry("holdReason", "Faltan los datos de pago");
        assertThat(post("/api/v1/admin/payouts/" + fila.get("id") + "/approve", adminSession, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        listaParaCobrar(mariaSession);

        Map despues = ((List<Map>) get("/api/v1/admin/payouts?periodStart=2026-07-01", adminSession, Map.class)
                .getBody().get("payouts")).getFirst();
        assertThat(despues).containsEntry("status", "DRAFT").containsEntry("holdReason", null);
    }

    /**
     * El panel del admin cuadra con las liquidaciones: la ganancia de una clase pasa de retenida a
     * por transferir a transferida, y en cada momento está en una sola de las tres.
     */
    @Test
    void theAdminPanelMoneyMovesFromHeldToPayableToTransferredWithoutCountingTwice() {
        DashboardResponse.Money antes = panel();

        UUID bookingId = bookAndPay(maria, 9);
        assertThat(delta(panel(), antes)).containsExactly(EARNINGS_COP, 0L, 0L, COMMISSION_COP);

        recordAttendance(bookingId, mariaSession);
        assertThat(delta(panel(), antes)).containsExactly(0L, EARNINGS_COP, 0L, COMMISSION_COP);

        // Liquidada pero sin transferir: sigue siendo algo que Orión tiene que pagar.
        listaParaCobrar(mariaSession);
        payoutService.runCut(PayoutCalculator.closedBy(LocalDate.of(2026, 7, 16)));
        assertThat(delta(panel(), antes)).containsExactly(0L, EARNINGS_COP, 0L, COMMISSION_COP);

        UUID payoutId = payoutService.ofFortnight(LocalDate.of(2026, 7, 1)).getFirst().getId();
        post("/api/v1/admin/payouts/" + payoutId + "/approve", adminSession, null, Map.class);
        post("/api/v1/admin/payouts/" + payoutId + "/pay", adminSession, pago("BREB-1", true), Map.class);
        assertThat(delta(panel(), antes)).containsExactly(0L, 0L, EARNINGS_COP, COMMISSION_COP);
    }

    private DashboardResponse.Money panel() {
        return get("/api/v1/admin/dashboard", adminSession, DashboardResponse.class).getBody().money();
    }

    /** Retenido, por transferir, transferido y comisión, como diferencia con la foto inicial. */
    private static long[] delta(DashboardResponse.Money ahora, DashboardResponse.Money antes) {
        return new long[] {
                ahora.heldCop() - antes.heldCop(),
                ahora.payableCop() - antes.payableCop(),
                ahora.transferredCop() - antes.transferredCop(),
                ahora.commissionEarnedCop() - antes.commissionEarnedCop()};
    }

    /** Una clase que no ocurrió no se le paga a nadie: la liquidación solo mira lo liberado. */
    @Test
    void aClassThatWasNeverGivenDoesNotEnterAPayout() {
        bookAndPay(maria, 9);   // pagada, pero sin registro de asistencia

        assertThat(payoutService.runCut(PayoutCalculator.closedBy(LocalDate.of(2026, 8, 1)))).isZero();
    }

    /** Si el profesor cancela, el estudiante recupera el valor completo de la clase como saldo. */
    @Test
    void aProfessorCancellationTurnsThePaymentIntoCreditForTheStudent() {
        UUID bookingId = bookAndPay(maria, 9);

        ResponseEntity<BookingResponse> cancelled = post(
                BOOKINGS + "/" + bookingId + "/cancel", mariaSession, null, BookingResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);

        assertThat(credits.findAll()).hasSize(1);
        assertThat(credits.findAll().get(0).getRemainingCop()).isEqualTo(RATE_COP);
        assertThat(credits.findAll().get(0).getReason().name()).isEqualTo("CANCELLED_BY_PROFESSOR");
    }

    @Test
    void onlyTheAdminReachesTheReconciliationScreen() {
        assertThat(get("/api/v1/admin/payments", anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/payments", mariaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/payments", adminSession, AdminPaymentResponse[].class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID bookAndPay(User professor, int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(professor.getId(), at, "VIRTUAL", null, null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode())
                .as("respuesta: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        approvePayment(response.getBody().id());
        return response.getBody().id();
    }

    /**
     * La asistencia solo se registra sobre una clase que ya terminó, y el reloj está congelado: se
     * mueve la CLASE al pasado en vez del reloj. Es el mismo hecho visto desde el otro lado.
     */
    private void recordAttendance(UUID bookingId, Session professorSession) {
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(7200)),
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(3600)),
                bookingId);

        ResponseEntity<Map> response = post(BOOKINGS + "/" + bookingId + "/attendance",
                professorSession, new RecordAttendanceRequest(true, null), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** María acepta el acuerdo con el mandato y registra su llave: si no, su liquidación queda retenida. */
    private void listaParaCobrar(Session sesion) {
        post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class);
        put("/api/v1/me/payout-details", sesion, Map.of("keyType", "PHONE", "key", "3001234567",
                "documentType", "CC", "documentNumber", "1020304050", "holderName", "María Gómez"), Map.class);
    }

    private static Map<String, Object> pago(String referencia, boolean titularVerificado) {
        return Map.of("paidOn", "2026-07-13", "reference", referencia, "holderVerified", titularVerificado);
    }

    private EarningsResponse earnings(Session session) {
        return get("/api/v1/me/earnings?from=2026-07-01&to=2026-07-31", session,
                EarningsResponse.class).getBody();
    }
}
