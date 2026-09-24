package co.orion.admin.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.admin.application.CorreoDePrueba;
import co.orion.admin.application.RehearsalService;
import co.orion.admin.application.SystemStatusService;
import co.orion.scheduling.application.TestClassService;
import co.orion.scheduling.domain.Booking;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Estado del despliegue y ensayos del aula y del acta. Solo admin: la ruta ya está bajo `/api/v1/admin/**`. */
@RestController
@RequestMapping("/api/v1/admin/system")
public class SystemStatusController {

    private final SystemStatusService status;
    private final TestClassService testClasses;
    private final RehearsalService ensayos;
    private final CorreoDePrueba correoDePrueba;

    public SystemStatusController(SystemStatusService status, TestClassService testClasses,
                                  RehearsalService ensayos, CorreoDePrueba correoDePrueba) {
        this.status = status;
        this.testClasses = testClasses;
        this.ensayos = ensayos;
        this.correoDePrueba = correoDePrueba;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return status.status();
    }

    /**
     * Crea una clase de prueba para ensayar el aula. No cobra, no manda correos y no cuenta en las
     * ganancias: nace confirmada y marcada como ensayo ({@code is_rehearsal}).
     */
    @PostMapping("/test-class")
    @ResponseStatus(HttpStatus.CREATED)
    public TestClassResponse testClass(@AuthenticationPrincipal OrionUserDetails principal,
                                       @Valid @RequestBody TestClassRequest body) {
        Instant inicio = body.startsAt() == null
                ? null
                : body.startsAt().atZone(BusinessZone.BOGOTA).toInstant();

        Booking creada = testClasses.create(
                principal.user(), body.studentEmail(), body.professorEmail(), inicio);

        return new TestClassResponse(
                creada.getId(),
                ZonedDateTime.ofInstant(creada.getStartsAt(), BusinessZone.BOGOTA),
                ZonedDateTime.ofInstant(creada.getEndsAt(), BusinessZone.BOGOTA),
                creada.getMeetingLink());
    }

    /**
     * Ensayo del acta y la práctica: una clase de prueba que ya se dictó, lista para que el profesor
     * escriba su acta. Tampoco cobra ni manda correos de reserva.
     */
    @PostMapping("/rehearsal")
    @ResponseStatus(HttpStatus.CREATED)
    public RehearsalResponse rehearsal(@AuthenticationPrincipal OrionUserDetails principal,
                                       @Valid @RequestBody RehearsalRequest body) {
        Booking creada = testClasses.createHeld(principal.user(), body.studentEmail(), body.professorEmail());
        return new RehearsalResponse(creada.getId(), "/mis-clases/" + creada.getId() + "/acta");
    }

    /** Los ensayos de la última semana, con su acta y su práctica: en qué va cada uno. */
    @GetMapping("/rehearsals")
    public RehearsalService.Ensayos rehearsals() {
        return ensayos.recientes();
    }

    /**
     * Un correo de verdad, por el transporte de verdad, a la dirección del admin o a la que escriba.
     * Responde 200 también cuando falla: el fallo es el resultado que se vino a buscar.
     */
    @PostMapping("/test-email")
    public CorreoDePrueba.Resultado testEmail(@AuthenticationPrincipal OrionUserDetails principal,
                                             @Valid @RequestBody(required = false) TestEmailRequest body) {
        return correoDePrueba.enviar(principal.user(), body == null ? null : body.to());
    }

    public record TestEmailRequest(@Email String to) {
    }

    public record RehearsalRequest(@NotBlank @Email String studentEmail, @NotBlank @Email String professorEmail) {
    }

    public record RehearsalResponse(java.util.UUID bookingId, String acta) {
    }

    /** La hora va en hora de Bogotá y sin zona: es la que el admin lee en su reloj. */
    public record TestClassRequest(
            @NotBlank @Email String studentEmail,
            @NotBlank @Email String professorEmail,
            LocalDateTime startsAt) {
    }

    public record TestClassResponse(java.util.UUID bookingId,
                                    ZonedDateTime startsAt,
                                    ZonedDateTime endsAt,
                                    String aula) {
    }
}
