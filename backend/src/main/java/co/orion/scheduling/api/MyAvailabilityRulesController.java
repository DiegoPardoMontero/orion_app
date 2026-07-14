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

import co.orion.scheduling.application.AvailabilityRuleService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/availability/rules")
public class MyAvailabilityRulesController {

    private final AvailabilityRuleService ruleService;

    public MyAvailabilityRulesController(AvailabilityRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<RuleResponse> list(@AuthenticationPrincipal OrionUserDetails principal) {
        return ruleService.listOwnRules(professorId(principal)).stream()
                .map(RuleResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@AuthenticationPrincipal OrionUserDetails principal,
                               @Valid @RequestBody CreateRuleRequest body) {
        return RuleResponse.from(ruleService.create(
                professorId(principal), body.weekday(), body.startTime(), body.endTime()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        ruleService.delete(professorId(principal), id);
    }

    private UUID professorId(OrionUserDetails principal) {
        return principal.user().getId();
    }
}
