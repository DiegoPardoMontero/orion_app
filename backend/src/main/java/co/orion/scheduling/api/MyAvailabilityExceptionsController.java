package co.orion.scheduling.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.AvailabilityExceptionService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/availability/exceptions")
public class MyAvailabilityExceptionsController {

    private final AvailabilityExceptionService exceptionService;

    public MyAvailabilityExceptionsController(AvailabilityExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @GetMapping
    public List<ExceptionResponse> list(@AuthenticationPrincipal OrionUserDetails principal) {
        return exceptionService.listUpcoming(professorId(principal)).stream()
                .map(ExceptionResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExceptionResponse create(@AuthenticationPrincipal OrionUserDetails principal,
                                    @Valid @RequestBody CreateExceptionRequest body) {
        return ExceptionResponse.from(exceptionService.create(
                professorId(principal), body.date(), body.startTime(), body.endTime(), body.reason()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        exceptionService.delete(professorId(principal), id);
    }

    private UUID professorId(OrionUserDetails principal) {
        return principal.user().getId();
    }
}
