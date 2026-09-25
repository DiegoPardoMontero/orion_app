package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.AdminUserService;
import co.orion.identity.application.FounderService;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.shared.error.BusinessRuleViolationException;
import jakarta.validation.Valid;

/** Toda la ruta /api/v1/admin/** exige rol ADMIN en la SecurityFilterChain. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUsersController {

    private final AdminUserService adminUsers;
    private final FounderService founders;

    public AdminUsersController(AdminUserService adminUsers, FounderService founders) {
        this.adminUsers = adminUsers;
        this.founders = founders;
    }

    @GetMapping
    public List<AdminUserResponse> list(@RequestParam(required = false) String role,
                                        @RequestParam(required = false) String q) {
        List<User> encontrados = adminUsers.search(parseRole(role), q);
        // El beneficio de fundador de cada profe de la lista, en una sola consulta.
        var fundadores = founders.viewsOf(encontrados.stream()
                .filter(u -> u.getRole() == UserRole.PROFESSOR).map(User::getId).toList());
        return encontrados.stream()
                .map(u -> AdminUserResponse.from(u, fundadores.get(u.getId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody CreateUserRequest body) {
        return AdminUserResponse.from(adminUsers.create(
                body.email(),
                body.fullName(),
                body.whatsappPhone(),
                requireRole(body.role()),
                body.password()));
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateUserRequest body) {
        return AdminUserResponse.from(
                adminUsers.update(id, body.fullName(), body.whatsappPhone(), body.status()));
    }

    /** Filtro opcional: sin rol, no se filtra. */
    private UserRole parseRole(String role) {
        return (role == null || role.isBlank()) ? null : requireRole(role);
    }

    private UserRole requireRole(String role) {
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("role debe ser STUDENT, PROFESSOR o ADMIN");
        }
    }
}
