package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.AdolescentProfileService;
import co.orion.identity.domain.ProficiencyLevel;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Los adolescentes a cargo del adulto (Bloque 12): crear con la autorización, editar, revocar. */
@RestController
@RequestMapping("/api/v1/me/adolescents")
public class MyAdolescentsController {

    private final AdolescentProfileService adolescents;

    public MyAdolescentsController(AdolescentProfileService adolescents) {
        this.adolescents = adolescents;
    }

    @GetMapping
    public List<AdolescentProfileResponse> list(@AuthenticationPrincipal OrionUserDetails principal) {
        return adolescents.mine(principal.user()).stream().map(AdolescentProfileResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdolescentProfileResponse create(@AuthenticationPrincipal OrionUserDetails principal,
                                            @Valid @RequestBody CreateRequest body,
                                            HttpServletRequest http) {
        return AdolescentProfileResponse.from(adolescents.create(principal.user(), body.firstName(), body.birthYear(),
                nivel(body.selfDeclaredLevel()), body.motivation(), body.acceptsAuthorization(), body.heardConfirmed(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
    }

    @PatchMapping("/{id}")
    public AdolescentProfileResponse update(@AuthenticationPrincipal OrionUserDetails principal,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody UpdateRequest body) {
        return AdolescentProfileResponse.from(adolescents.update(principal.user(), id, body.firstName(),
                nivel(body.selfDeclaredLevel()), body.motivation()));
    }

    /** Revocar la autorización: el perfil se cierra y lo suyo se borra. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        adolescents.revoke(principal.user(), id);
    }

    private static ProficiencyLevel nivel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        try {
            return ProficiencyLevel.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("selfDeclaredLevel debe ser BEGINNER, INTERMEDIATE o ADVANCED");
        }
    }

    public record CreateRequest(
            @NotBlank(message = "Dinos el nombre de pila del adolescente.") @Size(max = 60) String firstName,
            @Min(1990) @Max(2100) int birthYear,
            String selfDeclaredLevel,
            @Size(max = 500) String motivation,
            @AssertTrue(message = "Necesitamos tu autorización como representante legal.") boolean acceptsAuthorization,
            @AssertTrue(message = "Confirma que el adolescente fue escuchado y está de acuerdo.") boolean heardConfirmed) {
    }

    public record UpdateRequest(@Size(max = 60) String firstName, String selfDeclaredLevel,
                                @Size(max = 500) String motivation) {
    }
}
