package co.orion.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.orion.billing.persistence.PaymentCreditApplicationRepository;
import co.orion.billing.persistence.PaymentEventRepository;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.PayoutItemRepository;
import co.orion.billing.persistence.PayoutRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.lifecycle.persistence.DisputeRepository;
import co.orion.reputation.persistence.ProfessorSanctionRepository;
import co.orion.scheduling.persistence.ProfessorAbsenceRepository;
import co.orion.scheduling.persistence.RescheduleRequestRepository;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.StudentProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.AdminAuditLogRepository;
import co.orion.identity.persistence.AgreementAcceptanceRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.identity.persistence.UserRepository;

/**
 * Soporte para los tests de integración de la API: login, cookies de sesión y header CSRF.
 * Las anotaciones de Spring (@SpringBootTest, etc.) van en las clases concretas, no aquí.
 */
public abstract class ApiIntegrationSupport {

    protected static final String PASSWORD = "orion123*";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TeacherApplicationRepository teacherApplications;

    @Autowired
    private TeacherDocumentRepository teacherDocuments;

    @Autowired
    private AgreementAcceptanceRepository agreementAcceptances;

    @Autowired
    private AdminAuditLogRepository adminAuditLogs;

    @Autowired
    private PaymentEventRepository paymentEvents;

    @Autowired
    private PayoutItemRepository payoutItems;

    @Autowired
    private PayoutRepository payouts;

    @Autowired
    private PaymentCreditApplicationRepository creditApplications;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private StudentCreditRepository studentCredits;

    @Autowired
    private RescheduleRequestRepository rescheduleRequests;

    @Autowired
    private DisputeRepository disputes;

    @Autowired
    private ProfessorAbsenceRepository professorAbsences;

    @Autowired
    private ProfessorSanctionRepository professorSanctions;

    /**
     * Limpia ANTES de que el @BeforeEach de cada test haga users.deleteAll(). Estas tablas
     * referencian a users (y a bookings) SIN cascade, así que si quedaran filas el borrado de
     * usuarios fallaría por FK. El @BeforeEach de la superclase corre antes que el de la subclase.
     *
     * El orden es el de las dependencias, de la hoja a la raíz: lo que apunta a payments antes que
     * payments, y payments antes que bookings (que borra cada test).
     */
    @BeforeEach
    void cleanDependentTables() {
        adminAuditLogs.deleteAll();
        teacherDocuments.deleteAll();
        agreementAcceptances.deleteAll();
        teacherApplications.deleteAll(); // los eventos caen por ON DELETE CASCADE

        payoutItems.deleteAll();
        payouts.deleteAll();
        paymentEvents.deleteAll();
        creditApplications.deleteAll();
        payments.deleteAll();
        studentCredits.deleteAll();

        // Bloques 5 y 6: todas cuelgan de bookings o de users. professor_absences apunta además a
        // disputes, así que va antes que ellas.
        professorAbsences.deleteAll();
        professorSanctions.deleteAll();
        disputes.deleteAll();
        rescheduleRequests.deleteAll();
    }

    /**
     * Aprueba el pago de una reserva por el mismo camino que la realidad: un webhook FIRMADO de
     * Wompi. No hay atajo por el repositorio a propósito — media docena de tests dependen de que
     * una clase quede confirmada, y si llegaran ahí por la puerta de atrás nadie estaría probando
     * la puerta de entrada.
     */
    protected void approvePayment(UUID bookingId) {
        sendPaymentWebhook(bookingId, "APPROVED");
    }

    /** La otra cara: la pasarela rechaza y el cupo tiene que volver al mercado. */
    protected void declinePayment(UUID bookingId) {
        sendPaymentWebhook(bookingId, "DECLINED");
    }

    private void sendPaymentWebhook(UUID bookingId, String status) {
        var payment = payments.findByBookingId(bookingId).orElseThrow(
                () -> new IllegalStateException("La reserva " + bookingId + " no tiene pago"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = WompiWebhooks.signed("txn-" + bookingId, payment.getProviderReference(),
                status, payment.getChargedCop() * 100, 1_756_000_000L);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/webhooks/payments/wompi", new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("El webhook de prueba falló: " + response);
        }
    }

    /** Sesión autenticada: cookie de sesión + token CSRF, que es lo que exige toda petición mutante. */
    protected record Session(String cookie, String csrfToken) {
    }

    /**
     * Crea un usuario como lo haría el registro. Para los estudiantes eso incluye su ficha: en
     * producción nace con la cuenta (RegistrationService) o la creó la V21, así que un estudiante
     * sin ficha es un estado que no existe y los tests no deben fabricarlo.
     */
    @Autowired
    protected StudentProfileRepository studentProfiles;

    protected User createUser(String email, String fullName, UserRole role) {
        User user = users.save(new User(email, passwordEncoder.encode(PASSWORD), fullName, role));
        if (role == UserRole.STUDENT) {
            studentProfiles.save(new StudentProfile(user));
        }
        return user;
    }

    /** Da al profesor una postulación APPROVED: sin ella el gate de visibilidad lo ocultaría. */
    protected void approveTeacher(UUID userId) {
        teacherApplications.saveAndFlush(
                new TeacherApplication(userId, ApplicationStatus.APPROVED, null, Instant.now()));
    }

    protected Session login(String email) {
        ResponseEntity<Void> response = rest.postForEntity(
                "/api/v1/auth/login",
                new LoginBody(email, PASSWORD),
                Void.class);

        String session = cookieValue(response, "ORION_SESSION");
        String csrf = cookieValue(response, "XSRF-TOKEN");
        if (session == null) {
            throw new IllegalStateException("El login de " + email + " no devolvió cookie de sesión");
        }
        return new Session(session, csrf);
    }

    protected <T> ResponseEntity<T> get(String path, Session session, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(session)), responseType);
    }

    protected <T> ResponseEntity<T> post(String path, Session session, Object body, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(session)), responseType);
    }

    protected <T> ResponseEntity<T> put(String path, Session session, Object body, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(session)), responseType);
    }

    protected <T> ResponseEntity<T> patch(String path, Session session, Object body, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers(session)), responseType);
    }

    protected <T> ResponseEntity<T> delete(String path, Session session, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers(session)), responseType);
    }

    private HttpHeaders headers(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE,
                "ORION_SESSION=" + session.cookie() + "; XSRF-TOKEN=" + session.csrfToken());
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        return headers;
    }

    private String cookieValue(ResponseEntity<?> response, String name) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1, cookie.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    private record LoginBody(String email, String password) {
    }
}
