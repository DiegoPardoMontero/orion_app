package co.orion.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import co.orion.shared.error.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Los frenos de las puertas públicas: login, alta y recuperación de contraseña.
 *
 * <p>Vive aquí y no en un filtro porque el filtro tendría que releer el cuerpo de la petición para
 * saber de qué correo se trata, y para eso hay que envolver la petición y cachearla. En el
 * controlador el DTO ya está parseado; el freno cabe en una línea y se lee.
 *
 * <p><strong>Por qué el login se limita por (IP + correo) y no por correo solo.</strong> Limitar
 * por correo permite dejar sin acceso a cualquiera fallando adrede contra su dirección — un ataque
 * de denegación trivial y gratuito. Con la IP dentro de la clave, el atacante se agota a sí mismo
 * y el titular sigue entrando desde su casa. A cambio, quien tenga muchas IPs puede probar más;
 * por eso hay además un tope por IP, que es el que corta el barrido de contraseñas.
 */
@Component
public class IntentosDeAcceso {

    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_POR_IP_Y_CORREO = 5;
    private static final int MAX_LOGIN_POR_IP = 30;

    private static final Duration VENTANA_HORA = Duration.ofHours(1);
    private static final int MAX_ALTAS_POR_IP = 3;
    private static final int MAX_RECUPERACIONES_POR_CORREO = 3;

    private final RateLimiter limiter = new RateLimiter();
    private final Clock clock;

    public IntentosDeAcceso(Clock clock) {
        this.clock = clock;
    }

    public void antesDeLogin(HttpServletRequest request, String email) {
        Instant now = clock.instant();
        String ip = ipDe(request);

        exigir("login:ip:" + ip, MAX_LOGIN_POR_IP, VENTANA_LOGIN, now,
                "Demasiados intentos desde esta conexión. Espera unos minutos.");
        exigir("login:" + ip + ":" + normalizar(email), MAX_LOGIN_POR_IP_Y_CORREO, VENTANA_LOGIN,
                now, "Demasiados intentos con este correo. Espera unos minutos o "
                        + "recupera tu contraseña.");
    }

    /** Un login correcto olvida los fallos: quien acertó no debe arrastrar los intentos previos. */
    public void loginCorrecto(HttpServletRequest request, String email) {
        limiter.reset("login:" + ipDe(request) + ":" + normalizar(email));
    }

    public void antesDeRegistro(HttpServletRequest request) {
        exigir("registro:" + ipDe(request), MAX_ALTAS_POR_IP, VENTANA_HORA, clock.instant(),
                "Ya creaste varias cuentas desde aquí. Intenta de nuevo en una hora.");
    }

    public void antesDeRecuperar(String email) {
        exigir("recuperar:" + normalizar(email), MAX_RECUPERACIONES_POR_CORREO, VENTANA_HORA,
                clock.instant(),
                "Ya te enviamos varios enlaces. Revisa tu correo y la carpeta de spam.");
    }

    /** Solo para tests: olvida todos los intentos. Ver {@link RateLimiter#resetAll()}. */
    public void olvidarTodo() {
        limiter.resetAll();
    }

    private void exigir(String key, int limite, Duration ventana, Instant now, String mensaje) {
        if (!limiter.tryAcquire(key, limite, ventana, now)) {
            throw new TooManyRequestsException(mensaje, limiter.retryAfter(key, ventana, now));
        }
    }

    /**
     * La IP del cliente. En Railway hay un proxy delante y el backend ve HTTP plano, pero
     * {@code server.forward-headers-strategy=framework} ya hace que Spring resuelva el
     * {@code X-Forwarded-For} antes de llegar aquí. Leer la cabecera a mano volvería a abrir la
     * puerta a que cualquiera se invente su propia IP.
     */
    private String ipDe(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null ? "desconocida" : ip;
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
