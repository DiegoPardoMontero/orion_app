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
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // @Qualifier es obligatorio: mvcHandlerMappingIntrospector, un bean interno de Spring MVC,
    // también implementa CorsConfigurationSource y haría ambigua la inyección por tipo.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource,
                                    FreshPrincipalFilter freshPrincipal)
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
                        // El webhook lo llama Wompi, no un navegador: no hay cookie que proteger y
                        // exigir CSRF solo garantizaría que ningún evento entre nunca. Lo que lo
                        // protege es la firma del propio evento, verificada antes de tocar la base.
                        "/api/v1/webhooks/payments/**"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // Antes de autorizar, el principal se refresca contra la base: así una aprobación o una
            // baja de cuenta valen desde la siguiente petición y no desde el siguiente login.
            .addFilterBefore(freshPrincipal, AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/payments/**").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/invite").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/accept-invite").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Marketplace público: el catálogo y el directorio/búsqueda de profesores se ven sin
                // sesión (un visitante anónimo explora antes de registrarse; reservar sí exige login).
                .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                // Lista y detalle públicos; los cupos (/professors/{id}/slots) siguen tras sesión.
                .requestMatchers(HttpMethod.GET, "/api/v1/professors").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/professors/*").permitAll()
                // Las reseñas de un profesor son parte de su perfil público (dos segmentos: /*/reviews
                // no lo cubre /professors/*, que solo casa un segmento).
                .requestMatchers(HttpMethod.GET, "/api/v1/professors/*/reviews").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // El aspirante a profesor no tiene experiencia de estudiante: lo que puede hacer es
                // llevar su postulación, mantener su cuenta y leer sus avisos. Todo lo demás cuelga
                // de ROLE_STUDENT, que no tiene, así que se cierra solo.
                .requestMatchers("/api/v1/me/account").authenticated()
                // Postulación a profesor: cualquier usuario autenticado puede aspirar y llevar su wizard.
                .requestMatchers("/api/v1/teacher-applications").authenticated()
                .requestMatchers("/api/v1/me/teacher-application", "/api/v1/me/teacher-application/**").authenticated()
                .requestMatchers("/api/v1/me/agreements/**").authenticated()
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
                // Reclamar una clase es del estudiante: es su dinero el que está en juego.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/report-problem").hasRole("STUDENT")
                // "Mis clases" solo tiene sentido para quien asiste o imparte.
                .requestMatchers("/api/v1/me/bookings").hasAnyRole("STUDENT", "PROFESSOR")
                // El panel de progreso es del estudiante: mide clases tomadas, no clases dictadas.
                .requestMatchers("/api/v1/me/progress").hasRole("STUDENT")
                // La ficha propia del estudiante. La vista de OTRO estudiante vive en /students/**
                // y la abren los dos roles, porque las capas de visibilidad las aplica el servicio.
                .requestMatchers("/api/v1/me/student-profile", "/api/v1/me/student-profile/**")
                        .hasRole("STUDENT")
                .requestMatchers("/api/v1/students/*/profile")
                        .hasAnyRole("STUDENT", "PROFESSOR", "ADMIN")
                // La gamificación es del estudiante: mide lo que él ha recorrido.
                .requestMatchers("/api/v1/me/engagement", "/api/v1/me/achievements",
                        "/api/v1/me/cosmetics", "/api/v1/me/streak").hasRole("STUDENT")
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
                // Dinero: el saldo y el historial son del estudiante; las ganancias, del profesor.
                // El admin llega a lo mismo por /api/v1/admin/payments, que ya exige rol ADMIN.
                .requestMatchers("/api/v1/me/credits", "/api/v1/me/payments").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/api/v1/me/earnings").hasRole("PROFESSOR")
                // El estado del pago de una clase lo consulta su estudiante (el servicio comprueba
                // que la reserva sea suya y responde 404 si no lo es).
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/payment")
                        .hasAnyRole("STUDENT", "ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
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
