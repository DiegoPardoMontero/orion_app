package co.orion.shared.api;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.ServiceUnavailableException;
import co.orion.shared.error.TooManyRequestsException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.observability.AlertService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AlertService alerts;

    public GlobalExceptionHandler(AlertService alerts) {
        this.alerts = alerts;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Validation failed", "details", details));
    }

    /**
     * Credenciales malas y usuario inactivo responden idéntico a propósito: decir cuál de los
     * dos falló le regalaría al atacante la confirmación de que el email existe.
     */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid credentials"));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleViolationException ex) {
        if (ex.getDetails().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage(), "missing", ex.getDetails()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<Map<String, Object>> handleUnprocessable(UnprocessableException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Demasiados intentos: 429 con {@code Retry-After} en segundos, como manda el estándar. Un
     * cliente decente lo respeta solo; el nuestro además lo enseña.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(TooManyRequestsException ex) {
        long segundos = Math.max(1, ex.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(segundos))
                .body(Map.of("error", ex.getMessage(), "retryAfterSeconds", segundos));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /** Un archivo por encima del límite: 422, no el 500 genérico. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "El archivo supera el tamaño máximo permitido"));
    }

    /** Un JSON malformado o un enum inválido es culpa del cliente: 400, no 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "El cuerpo de la petición no es válido"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", message));
    }

    /** Una ruta que no existe es 404, no 500. Si no, cualquier URL inválida parecería un fallo del servidor. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recurso no encontrado"));
    }

    /**
     * El verbo equivocado sobre una ruta que sí existe es 405, no 500. Caía en el cajón de sastre,
     * y entonces un `POST` donde iba un `PUT` se veía igual que una base caída: mismo 500, mismo
     * «Unexpected error», y una traza en el log que hacía perder la tarde.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Este recurso no admite " + ex.getMethod()));
    }

    /**
     * Una integración que falta o falla. Se distingue del 500 genérico porque aquí sí se sabe qué
     * pasó y se puede decir: quien intenta subir su foto y recibe «Unexpected error» no tiene forma
     * de saber que lo que falta es una variable de entorno.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(ServiceUnavailableException ex) {
        log.error("Integración no disponible: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Lo que llegó a 500. Se registra y, además, se avisa por correo.
     *
     * <p>El aviso lleva ruta, método, id de usuario si lo hay y la traza recortada. NUNCA el cuerpo
     * de la petición, ni cookies, ni cabeceras de autorización: una alerta que arrastra el cuerpo
     * de un POST acaba llevando una contraseña a una bandeja de Gmail, y entonces el sistema de
     * alertas es el incidente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex,
                                                                HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        alerts.alert(AlertService.firmaDe(ex),
                "Error no controlado en " + request.getRequestURI(),
                "Método: " + request.getMethod()
                        + "\nRuta: " + request.getRequestURI()
                        + "\nUsuario: " + usuarioDe()
                        + "\n\n" + AlertService.trazaCorta(ex, 8));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected error"));
    }

    /** Solo el id: el nombre y el correo de una persona no tienen por qué viajar a una alerta. */
    private String usuarioDe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getPrincipal() instanceof String) {
            return "anónimo";
        }
        return auth.getPrincipal() instanceof OrionUserDetails details
                ? String.valueOf(details.user().getId())
                : "autenticado";
    }
}
