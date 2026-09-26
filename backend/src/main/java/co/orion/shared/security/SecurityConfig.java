package co.orion.shared.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import co.orion.identity.api.SocialLogin;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // @Qualifier es obligatorio: mvcHandlerMappingIntrospector, un bean interno de Spring MVC,
    // también implementa CorsConfigurationSource y haría ambigua la inyección por tipo.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource,
                                    FreshPrincipalFilter freshPrincipal,
                                    SocialLogin social)
            throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // Endpoints anónimos: no hay sesión ni token todavía que exigir.
                .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                        "/api/v1/auth/accept-invite",
                        // Se llega desde el enlace del correo, a veces en otro navegador: no hay
                        // cookie CSRF que presentar. Lo que autoriza es el token del enlace, que
                        // es de un solo uso y caduca.
                        "/api/v1/auth/verify-email",
                        // El webhook lo llama Wompi, no un navegador: no hay cookie que proteger y
                        // exigir CSRF solo garantizaría que ningún evento entre nunca. Lo que lo
                        // protege es la firma del propio evento, verificada antes de tocar la base.
                        "/api/v1/webhooks/payments/**",
                        // Lo mismo con los eventos de la sala de 8x8: los protege su firma.
                        "/api/v1/webhooks/video/**",
                        // Apple vuelve con un POST desde su dominio: no puede traer nuestro token.
                        // Lo que protege esa vuelta es el `state` de OAuth, que Spring comprueba.
                        // Completar el alta tras volver del proveedor NO está aquí: crea una cuenta
                        // y abre sesión, y la pantalla que lo llama ya tiene el token.
                        "/login/oauth2/code/*"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // Antes de autorizar, el principal se refresca contra la base: así una aprobación o una
            // baja de cuenta valen desde la siguiente petición y no desde el siguiente login.
            .addFilterBefore(freshPrincipal, AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                // Entrar con Google, Apple o Facebook: la ida, la vuelta y lo que la rodea.
                .requestMatchers("/oauth2/**", "/login/oauth2/**", "/api/v1/auth/social/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/payments/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/video/**").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                // Quien llega desde su buzón puede no tener sesión abierta, o tenerla en otro
                // navegador. Exigirle entrar para confirmar su correo es pedirle que resuelva el
                // problema antes de resolverlo.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/invite").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/accept-invite").permitAll()
                // Términos, política de datos y contacto del responsable: abiertos porque el
                // art. 50 de la Ley 1480 de 2011 exige que estén disponibles ANTES de contratar.
                // Un documento que solo se ve tras iniciar sesión llega tarde.
                .requestMatchers(HttpMethod.GET, "/api/v1/legal/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Marketplace público: el catálogo y el directorio/búsqueda de profesores se ven sin
                // sesión (un visitante anónimo explora antes de registrarse; reservar sí exige login).
                .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                // Lista y detalle públicos; los cupos (/professors/{id}/slots) siguen tras sesión.
                .requestMatchers(HttpMethod.GET, "/api/v1/professors").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/professors/*").permitAll()
                // El enlace para invitar lo abre cualquiera, como el perfil al que lleva.
                .requestMatchers(HttpMethod.GET, "/api/v1/professors/by-slug/*").permitAll()
                // La clase de prueba se consulta con sesión: depende de quién pregunta.
                .requestMatchers(HttpMethod.GET, "/api/v1/professors/*/trial").hasRole("STUDENT")
                // Las reseñas de un profesor son parte de su perfil público (dos segmentos: /*/reviews
                // no lo cubre /professors/*, que solo casa un segmento).
                .requestMatchers(HttpMethod.GET, "/api/v1/professors/*/reviews").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // El aspirante a profesor no tiene experiencia de estudiante: lo que puede hacer es
                // llevar su postulación, mantener su cuenta y leer sus avisos. Todo lo demás cuelga
                // de ROLE_STUDENT, que no tiene, así que se cierra solo.
                .requestMatchers("/api/v1/me/account", "/api/v1/me/account/**").authenticated()
                // Postulación a profesor: la llevan quien entró por «Quiero enseñar» y el profesor
                // invitado por el admin. Un estudiante no postula desde su cuenta (Pardo, 25/09/2026):
                // aprobarla la convertiría en cuenta de profesor con sus clases y su saldo adentro.
                // Leer la suya sí puede cualquiera con sesión: el aspirante rechazado vuelve a ser
                // estudiante, y el aviso de la decisión lo lleva a verla.
                .requestMatchers(HttpMethod.GET, "/api/v1/me/teacher-application").authenticated()
                .requestMatchers("/api/v1/teacher-applications").hasAnyRole("TEACHER_APPLICANT", "PROFESSOR")
                .requestMatchers("/api/v1/me/teacher-application", "/api/v1/me/teacher-application/**")
                        .hasAnyRole("TEACHER_APPLICANT", "PROFESSOR")
                .requestMatchers("/api/v1/me/agreements/**").hasAnyRole("TEACHER_APPLICANT", "PROFESSOR")
                .requestMatchers("/api/v1/me/availability/**").hasRole("PROFESSOR")
                // /** para cubrir también /me/profile/rate y /me/profile/rate/preview (solo profesor).
                .requestMatchers("/api/v1/me/profile/**").hasRole("PROFESSOR")
                .requestMatchers("/api/v1/me/profile").hasRole("PROFESSOR")
                // Reservar es cosa de estudiantes (y de un admin en nombre de uno): un profesor no.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasAnyRole("STUDENT", "ADMIN")
                // Reprogramar dejó de ser una acción unilateral: se PROPONE y la contraparte
                // responde, así que los dos lados pueden hacer ambas cosas.
                .requestMatchers("/api/v1/bookings/*/reschedule-requests").hasAnyRole("STUDENT", "PROFESSOR", "ADMIN")
                .requestMatchers("/api/v1/reschedule-requests/**").hasAnyRole("STUDENT", "PROFESSOR", "ADMIN")
                .requestMatchers("/api/v1/me/reschedule-requests").hasAnyRole("STUDENT", "PROFESSOR")
                // El hilo de Rigel: mensajes oficiales, de estudiantes y profesores.
                .requestMatchers("/api/v1/me/rigel", "/api/v1/me/rigel/**").hasAnyRole("STUDENT", "PROFESSOR")
                // El diagnóstico de confianza es del estudiante: el profesor no se autoevalúa aquí,
                // y el aspirante todavía no tiene experiencia de estudiante. El servicio vuelve a
                // comprobar la propiedad y responde 404 si la evaluación no es suya.
                // El diagnóstico se hace también sin cuenta (22/09/2026): su dueño puede ser un
                // estudiante o el lead de este dispositivo, y AssessmentController decide cuál —
                // 401 sin ninguno de los dos, 403 con una cuenta que no es de estudiante.
                .requestMatchers("/api/v1/assessment-leads", "/api/v1/assessment-leads/**").permitAll()
                // «¿Prefieres que te llame una persona?»: sin cuenta, con freno por IP.
                .requestMatchers(HttpMethod.POST, "/api/v1/callback-requests").permitAll()
                .requestMatchers("/api/v1/assessments", "/api/v1/assessments/**").permitAll()
                .requestMatchers("/api/v1/me/assessments").permitAll()
                .requestMatchers("/api/v1/me/voice-consent", "/api/v1/me/voice-consent/**")
                        .authenticated()
                // El diagnóstico de un estudiante, para su profesor. El controlador comprueba que
                // exista reserva entre los dos y responde 404 si no: el de un desconocido no existe.
                .requestMatchers("/api/v1/professors/me/students/*/assessment").hasRole("PROFESSOR")
                // Cuánto habló su estudiante en sus clases juntos (webhook de JaaS). El servicio
                // exige además que haya habido reserva entre los dos.
                .requestMatchers("/api/v1/professors/me/students/*/classroom").hasRole("PROFESSOR")
                // El acta de clase (Bloque 10): la escribe, corrige y publica el profesor; la lee
                // también el estudiante. El servicio exige además ser de esa reserva (403 a otro
                // profesor, 404 a un tercero y al estudiante mientras es borrador).
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/lesson-note/draft").hasRole("PROFESSOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/lesson-note/dictation").hasRole("PROFESSOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/lesson-note").hasAnyRole("STUDENT", "PROFESSOR")
                .requestMatchers("/api/v1/lesson-notes/**").hasRole("PROFESSOR")
                .requestMatchers("/api/v1/me/lesson-notes", "/api/v1/me/lesson-notes/**")
                        .hasAnyRole("STUDENT", "PROFESSOR")
                // La práctica (Bloque 10, Parte B): del estudiante; el servicio exige además que el set
                // sea suyo (404 si no). El profesor ve el resumen y el historial de sus estudiantes.
                .requestMatchers("/api/v1/me/practice", "/api/v1/me/practice/**").hasRole("STUDENT")
                .requestMatchers("/api/v1/practice-sets/**", "/api/v1/practice-items/**").hasRole("STUDENT")
                .requestMatchers("/api/v1/professors/me/students/*/practice",
                        "/api/v1/professors/me/students/*/practice-sets").hasRole("PROFESSOR")
                // Cuántas de sus clases tienen acta: informativo, en su desempeño.
                .requestMatchers("/api/v1/professors/me/lesson-notes/**").hasRole("PROFESSOR")
                // El aula. Los dos lados entran; el servicio comprueba que la reserva sea suya y
                // responde 404 si no lo es, para no confirmarle a un extraño que la clase existe.
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/classroom")
                        .hasAnyRole("STUDENT", "PROFESSOR")
                // Reclamar una clase es del estudiante: es su dinero el que está en juego.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/report-problem").hasRole("STUDENT")
                // "Mis clases" solo tiene sentido para quien asiste o imparte.
                .requestMatchers("/api/v1/me/bookings").hasAnyRole("STUDENT", "PROFESSOR")
                // El retracto es del estudiante y solo sobre sus propias clases; el servicio
                // vuelve a comprobar la propiedad y responde 404 si la reserva no es suya.
                .requestMatchers("/api/v1/me/bookings/*/retraction").hasRole("STUDENT")
                // El panel de progreso es del estudiante: mide clases tomadas, no clases dictadas.
                .requestMatchers("/api/v1/me/progress").hasRole("STUDENT")
                // La ficha propia del estudiante. La vista de OTRO estudiante vive en /students/**
                // y la abren los dos roles, porque las capas de visibilidad las aplica el servicio.
                .requestMatchers("/api/v1/me/student-profile", "/api/v1/me/student-profile/**")
                        .hasRole("STUDENT")
                .requestMatchers("/api/v1/students/*/profile", "/api/v1/students/*/points")
                        .hasAnyRole("STUDENT", "PROFESSOR", "ADMIN")
                // La bienvenida: el video y los recorridos. El servicio comprueba que el paso sea del rol.
                .requestMatchers("/api/v1/me/onboarding", "/api/v1/me/onboarding/**")
                        .hasAnyRole("STUDENT", "PROFESSOR")
                // La gamificación es del estudiante: mide lo que él ha recorrido.
                .requestMatchers("/api/v1/me/engagement", "/api/v1/me/achievements",
                        "/api/v1/me/cosmetics", "/api/v1/me/streak", "/api/v1/me/points").hasRole("STUDENT")
                // La asistencia la registra quien dio la clase.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/attendance").hasRole("PROFESSOR")
                // Reseñar una clase es del estudiante; reportar una reseña, del profesor reseñado.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/review").hasRole("STUDENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/reviews/*/report").hasRole("PROFESSOR")
                // Mensajería: los dos lados pueden abrir un hilo, leerlo y responder. Quién puede
                // escribirle a quién no se decide aquí sino en el servicio, porque las dos reglas no
                // son la misma: el estudiante escribe a cualquier profesor aprobado, el profesor solo
                // a estudiantes que ya reservaron con él. El servicio también verifica que el hilo
                // sea suyo (403 a terceros).
                .requestMatchers("/api/v1/conversations", "/api/v1/conversations/**")
                        .hasAnyRole("STUDENT", "PROFESSOR")
                // Las notificaciones in-app son de cualquier usuario autenticado (cae en anyRequest,
                // pero se deja explícito por claridad junto al resto del Bloque 3).
                .requestMatchers("/api/v1/me/notifications", "/api/v1/me/notifications/**").authenticated()
                // Los avisos en el dispositivo: cada quien suscribe, apaga y prueba los suyos.
                .requestMatchers("/api/v1/push/config", "/api/v1/me/push-subscriptions",
                        "/api/v1/me/push-subscriptions/**").authenticated()
                // Soporte: cualquiera que esté dentro. Un profesor que no puede reclamar
                // formalmente su pago es un problema que vuelve por otro lado, y peor.
                .requestMatchers("/api/v1/me/support", "/api/v1/me/support/**").authenticated()
                // Dinero: el saldo y el historial son del estudiante; las ganancias, del profesor.
                // El admin llega a lo mismo por /api/v1/admin/payments, que ya exige rol ADMIN.
                .requestMatchers("/api/v1/me/credits", "/api/v1/me/payments").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/api/v1/me/earnings").hasRole("PROFESSOR")
                // A dónde se le paga: solo el profe, y enmascarado (brief de liquidaciones, paso 2).
                .requestMatchers("/api/v1/me/payout-details").hasRole("PROFESSOR")
                // El estado del pago de una clase lo consulta su estudiante (el servicio comprueba
                // que la reserva sea suya y responde 404 si no lo es).
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/payment")
                        .hasAnyRole("STUDENT", "ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // Sin «petición guardada»: Spring la guarda en la sesión en cada 401 para volver a ella
            // tras un formulario de login, que aquí no existe (los errores son JSON y el frontend
            // decide adónde ir). Guardarla creaba una sesión por cada visitante anónimo, y desde la
            // V68 cada sesión es una fila en Postgres.
            .requestCache(r -> r.requestCache(new NullRequestCache()));

        // Solo si hay algún proveedor configurado: sin ninguno, Spring no admite un login OAuth2
        // vacío, y tampoco hay nada que enchufar.
        if (social.encendido()) {
            social.configurar(http);
        }
        http
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new JsonAuthEntryPoint())
                .accessDeniedHandler(new JsonAccessDeniedHandler()))
            .logout(l -> l
                .logoutUrl("/api/v1/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("ORION_SESSION")
                .logoutSuccessHandler((req, res, a) -> res.setStatus(204)))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${orion.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        // Sin esto el navegador no manda la cookie de sesión en peticiones cross-origin.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
