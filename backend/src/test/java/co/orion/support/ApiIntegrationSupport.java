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

import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.AdminAuditLogRepository;
import co.orion.identity.persistence.AgreementAcceptanceRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
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

    /**
     * Limpia las tablas del Bloque 2 ANTES de que el @BeforeEach de cada test haga users.deleteAll():
     * teacher_applications referencia a users SIN cascade, así que si quedaran filas el borrado de
     * usuarios fallaría por FK. El @BeforeEach de la superclase corre antes que el de la subclase.
     */
    @BeforeEach
    void cleanBlock2Tables() {
        adminAuditLogs.deleteAll();
        teacherDocuments.deleteAll();
        agreementAcceptances.deleteAll();
        teacherApplications.deleteAll(); // los eventos caen por ON DELETE CASCADE
    }

    /** Sesión autenticada: cookie de sesión + token CSRF, que es lo que exige toda petición mutante. */
    protected record Session(String cookie, String csrfToken) {
    }

    protected User createUser(String email, String fullName, UserRole role) {
        return users.save(new User(email, passwordEncoder.encode(PASSWORD), fullName, role));
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
