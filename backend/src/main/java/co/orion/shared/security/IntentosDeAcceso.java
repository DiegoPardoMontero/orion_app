package co.orion.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
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
    private static final Duration VENTANA_HORA = Duration.ofHours(1);
    private static final Duration VENTANA_DIA = Duration.ofDays(1);

    private final RateLimiter limiter = new RateLimiter();
    private final Clock clock;
    private final int maxLoginPorIpYCorreo;
    private final int maxLoginPorIp;
    private final int maxAltasPorIp;
    private final int maxRecuperacionesPorCorreo;
    private final int maxRecuperacionesPorIp;
    private final int maxDiagnosticosAnonimosPorIp;
    private final int maxSesionesDeVozPorPersona;
    private final int maxTraduccionesPorDiagnostico;

    /**
     * Los topes son configurables porque el valor correcto depende de por dónde entra la gente.
     *
     * <p>El de altas empezó en 3 por hora y era demasiado estricto: <strong>varias personas
     * comparten una IP</strong> —una oficina, un colegio, un café—, y la cuarta que se registrara
     * en una hora se habría quedado fuera sin entender por qué. Diez sigue cortando la creación
     * automatizada de cuentas, que es de lo que hay que protegerse, sin castigar a una sala llena
     * de gente detrás del mismo router.
     */
    public IntentosDeAcceso(
            Clock clock,
            @Value("${orion.security.rate-limit.login-per-ip-and-email:5}") int maxLoginPorIpYCorreo,
            @Value("${orion.security.rate-limit.login-per-ip:60}") int maxLoginPorIp,
            @Value("${orion.security.rate-limit.signups-per-ip:10}") int maxAltasPorIp,
            @Value("${orion.security.rate-limit.password-resets:3}") int maxRecuperacionesPorCorreo,
            @Value("${orion.security.rate-limit.password-resets-per-ip:20}") int maxRecuperacionesPorIp,
            @Value("${orion.security.rate-limit.anonymous-assessments-per-ip:5}") int maxDiagnosticosAnonimosPorIp,
            @Value("${orion.security.rate-limit.voice-sessions-per-person:10}") int maxSesionesDeVozPorPersona,
            @Value("${orion.security.rate-limit.translations-per-assessment:80}") int maxTraduccionesPorDiagnostico) {
        this.clock = clock;
        this.maxLoginPorIpYCorreo = maxLoginPorIpYCorreo;
        this.maxLoginPorIp = maxLoginPorIp;
        this.maxAltasPorIp = maxAltasPorIp;
        this.maxRecuperacionesPorCorreo = maxRecuperacionesPorCorreo;
        this.maxRecuperacionesPorIp = maxRecuperacionesPorIp;
        this.maxDiagnosticosAnonimosPorIp = maxDiagnosticosAnonimosPorIp;
        this.maxSesionesDeVozPorPersona = maxSesionesDeVozPorPersona;
        this.maxTraduccionesPorDiagnostico = maxTraduccionesPorDiagnostico;
    }

    public void antesDeLogin(HttpServletRequest request, String email) {
        Instant now = clock.instant();
        String ip = ipDe(request);

        exigir("login:ip:" + ip, maxLoginPorIp, VENTANA_LOGIN, now,
                "Demasiados intentos desde esta conexión. Espera unos minutos.");
        exigir("login:" + ip + ":" + normalizar(email), maxLoginPorIpYCorreo, VENTANA_LOGIN,
                now, "Demasiados intentos con este correo. Espera unos minutos o "
                        + "recupera tu contraseña.");
    }

    /** Un login correcto olvida los fallos: quien acertó no debe arrastrar los intentos previos. */
    public void loginCorrecto(HttpServletRequest request, String email) {
        limiter.reset("login:" + ipDe(request) + ":" + normalizar(email));
    }

    public void antesDeRegistro(HttpServletRequest request) {
        exigir("registro:" + ipDe(request), maxAltasPorIp, VENTANA_HORA, clock.instant(),
                "Ya creaste varias cuentas desde aquí. Intenta de nuevo en una hora.");
    }

    /**
     * Tres por correo cuidan el buzón de cada quien; el tope por conexión corta al que rota
     * correos para usar nuestro SMTP como cañón de spam —cada petición es un correo que sale con
     * nuestro remitente y nuestra reputación—.
     */
    public void antesDeRecuperar(HttpServletRequest request, String email) {
        Instant now = clock.instant();
        exigir("recuperar:ip:" + ipDe(request), maxRecuperacionesPorIp, VENTANA_HORA, now,
                "Demasiadas solicitudes desde esta conexión. Intenta de nuevo en una hora.");
        exigir("recuperar:" + normalizar(email), maxRecuperacionesPorCorreo, VENTANA_HORA, now,
                "Ya te enviamos varios enlaces. Revisa tu correo y la carpeta de spam.");
    }

    /**
     * El diagnóstico sin cuenta abre al público una conversación que cuesta dinero. Cinco por
     * conexión al día dejan hacerlo a una familia o a una oficina, y cortan a quien quiera vaciar
     * el presupuesto a golpe de script. El tope diario de gasto es la otra mitad del freno.
     */
    public void antesDeDiagnosticoAnonimo(HttpServletRequest request) {
        exigir("diagnostico:" + ipDe(request), maxDiagnosticosAnonimosPorIp, VENTANA_DIA,
                clock.instant(),
                "Ya se hicieron varios diagnósticos desde esta conexión hoy. Vuelve mañana, o crea tu "
                        + "cuenta y hazlo desde ella.");
    }

    /**
     * Cada sesión de voz es una llave del proveedor que se paga, y retomar un diagnóstico abierto
     * entrega otra. Sin tope, un solo lead —o una cuenta— podía pedir llaves sin fin y gastar el
     * presupuesto del día entero. Diez al día cubren reconexiones de sobra.
     *
     * @param persona el id de la cuenta o del lead: los dos tienen el mismo tope
     */
    public void antesDeAbrirVoz(java.util.UUID persona) {
        exigir("voz:" + persona, maxSesionesDeVozPorPersona, VENTANA_DIA, clock.instant(),
                "Ya intentaste el diagnóstico varias veces hoy. Vuelve mañana.");
    }

    /**
     * Dos minutos de Meissa son unas veinte frases. Ochenta por diagnóstico sobran para eso y
     * cortan a quien use la traducción como un traductor gratis con nuestra llave.
     */
    public void antesDeTraducir(java.util.UUID diagnostico) {
        exigir("traducir:" + diagnostico, maxTraduccionesPorDiagnostico, VENTANA_DIA, clock.instant(),
                "Ya no hay más traducciones para esta conversación.");
    }

    /** El «te llamamos» es público y deja un teléfono a nuestro cargo: cinco por conexión al día. */
    public void antesDeSolicitarLlamada(HttpServletRequest request) {
        exigir("llamada:" + ipDe(request), maxDiagnosticosAnonimosPorIp, VENTANA_DIA, clock.instant(),
                "Ya recibimos varias solicitudes desde esta conexión hoy. Te escribimos pronto.");
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
