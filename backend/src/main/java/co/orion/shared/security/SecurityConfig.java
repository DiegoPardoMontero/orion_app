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
                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // Endpoints anónimos: no hay sesión ni token todavía que exigir.
                .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                        "/api/v1/auth/accept-invite"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
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
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
                // Reprogramar es acción del estudiante (o del admin), nunca del profesor.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/reschedule").hasAnyRole("STUDENT", "ADMIN")
                // "Mis clases" solo tiene sentido para quien asiste o imparte.
                .requestMatchers("/api/v1/me/bookings").hasAnyRole("STUDENT", "PROFESSOR")
                // La asistencia la registra quien dio la clase.
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/attendance").hasRole("PROFESSOR")
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
