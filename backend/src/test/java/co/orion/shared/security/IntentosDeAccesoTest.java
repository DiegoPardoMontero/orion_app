package co.orion.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import co.orion.shared.error.TooManyRequestsException;

class IntentosDeAccesoTest {

    private final IntentosDeAcceso intentos = new IntentosDeAcceso(
            Clock.fixed(Instant.parse("2026-09-22T20:00:00Z"), ZoneOffset.UTC), 5, 60, 10, 3, 20, 5, 10, 80);

    private static MockHttpServletRequest desde(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    @DisplayName("Recuperar: rotar correos desde una conexión no convierte el SMTP en cañón de spam")
    void recuperarTieneTopePorConexion() {
        for (int i = 0; i < 20; i++) {
            intentos.antesDeRecuperar(desde("10.0.0.1"), "victima" + i + "@correo.test");
        }

        assertThatThrownBy(() -> intentos.antesDeRecuperar(desde("10.0.0.1"), "otra@correo.test"))
                .isInstanceOf(TooManyRequestsException.class);
        // Otra conexión, sin culpa, sigue pudiendo.
        assertThatCode(() -> intentos.antesDeRecuperar(desde("10.0.0.2"), "otra@correo.test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Recuperar: tres enlaces por correo, aunque vengan de conexiones distintas")
    void recuperarTieneTopePorCorreo() {
        for (int i = 0; i < 3; i++) {
            intentos.antesDeRecuperar(desde("10.0.1." + i), "Ana@Correo.test ");
        }

        assertThatThrownBy(() -> intentos.antesDeRecuperar(desde("10.0.2.1"), "ana@correo.test"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    @DisplayName("Login: fallar contra el correo de otro desde mi conexión no lo deja fuera a él")
    void loginNoBloqueaAlTitular() {
        for (int i = 0; i < 5; i++) {
            intentos.antesDeLogin(desde("10.0.0.9"), "ana@correo.test");
        }

        assertThatThrownBy(() -> intentos.antesDeLogin(desde("10.0.0.9"), "ana@correo.test"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> intentos.antesDeLogin(desde("10.0.0.10"), "ana@correo.test"))
                .doesNotThrowAnyException();
    }
}
