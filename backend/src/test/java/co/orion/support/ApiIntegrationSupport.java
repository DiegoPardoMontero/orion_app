package co.orion.support;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
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

    /** Sesión autenticada: cookie de sesión + token CSRF, que es lo que exige toda petición mutante. */
    protected record Session(String cookie, String csrfToken) {
    }

    protected User createUser(String email, String fullName, UserRole role) {
        return users.save(new User(email, passwordEncoder.encode(PASSWORD), fullName, role));
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
