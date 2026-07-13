package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate // en Boot 4 el bean TestRestTemplate ya no se registra solo
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

    private static final String STUDENT_EMAIL = "ana@orion.test";
    private static final String ADMIN_EMAIL = "admin@orion.test";
    private static final String INACTIVE_EMAIL = "inactivo@orion.test";
    private static final String PASSWORD = "orion123*";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        users.deleteAll();
        users.save(new User(STUDENT_EMAIL, passwordEncoder.encode(PASSWORD), "Ana Ramírez", UserRole.STUDENT));
        users.save(new User(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), "Orion Admin", UserRole.ADMIN));

        User inactive = new User(INACTIVE_EMAIL, passwordEncoder.encode(PASSWORD), "Ex Alumno", UserRole.STUDENT);
        inactive.deactivate();
        users.save(inactive);
    }

    @Test
    void aValidLoginReturnsTheUserAndOpensASession() {
        ResponseEntity<UserResponse> response = login(STUDENT_EMAIL, PASSWORD, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo(STUDENT_EMAIL);
        assertThat(response.getBody().role()).isEqualTo("STUDENT");
        assertThat(sessionCookie(response)).isNotNull();
    }

    @Test
    void aWrongPasswordIsRejectedWithoutRevealingWhyItFailed() {
        ResponseEntity<Map> response = login(STUDENT_EMAIL, "clave-mala", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // El mensaje no puede decir si falló el email o la clave: sería un oráculo de emails.
        assertThat(response.getBody()).containsEntry("error", "Invalid credentials");
    }

    @Test
    void anInactiveUserCannotLogIn() {
        ResponseEntity<Map> response = login(INACTIVE_EMAIL, PASSWORD, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Idéntico a una clave errada: tampoco revelamos que la cuenta existe pero está inactiva.
        assertThat(response.getBody()).containsEntry("error", "Invalid credentials");
    }

    @Test
    void anonymousAccessToMeIsUnauthorized() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/auth/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Authentication required");
    }

    @Test
    void aStudentIsForbiddenFromTheAdminArea() {
        String session = sessionCookie(login(STUDENT_EMAIL, PASSWORD, UserResponse.class));

        ResponseEntity<Map> response = get("/api/v1/admin/ping", session, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Access denied");
    }

    @Test
    void anAdminReachesTheAdminArea() {
        String session = sessionCookie(login(ADMIN_EMAIL, PASSWORD, UserResponse.class));

        ResponseEntity<Map> response = get("/api/v1/admin/ping", session, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("pong", true);
    }

    @Test
    void aLoggedInUserSeesThemselvesInMe() {
        String session = sessionCookie(login(STUDENT_EMAIL, PASSWORD, UserResponse.class));

        ResponseEntity<UserResponse> response = get("/api/v1/auth/me", session, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isEqualTo(STUDENT_EMAIL);
    }

    @Test
    void logoutInvalidatesTheSession() {
        ResponseEntity<UserResponse> loginResponse = login(STUDENT_EMAIL, PASSWORD, UserResponse.class);
        String session = sessionCookie(loginResponse);
        String csrfToken = cookieValue(loginResponse, "XSRF-TOKEN");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "ORION_SESSION=" + session + "; XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);

        ResponseEntity<Void> logout = rest.exchange(
                "/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> afterLogout = get("/api/v1/auth/me", session, Map.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMutatingRequestWithoutTheCsrfHeaderIsRejected() {
        String session = sessionCookie(login(STUDENT_EMAIL, PASSWORD, UserResponse.class));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "ORION_SESSION=" + session);

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        // Sin el header X-XSRF-TOKEN la petición mutante muere: esto es CSRF haciendo su trabajo.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private <T> ResponseEntity<T> login(String email, String password, Class<T> responseType) {
        return rest.postForEntity("/api/v1/auth/login", new LoginRequest(email, password), responseType);
    }

    private <T> ResponseEntity<T> get(String path, String session, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "ORION_SESSION=" + session);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private String sessionCookie(ResponseEntity<?> response) {
        return cookieValue(response, "ORION_SESSION");
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
}
