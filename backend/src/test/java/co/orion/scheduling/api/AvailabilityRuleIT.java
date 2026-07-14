package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AvailabilityRuleIT extends ApiIntegrationSupport {

    private static final String RULES = "/api/v1/me/availability/rules";

    @Autowired
    private AvailabilityRuleRepository rules;

    private User maria;
    private User juan;
    private Session mariaSession;

    @BeforeEach
    void seed() {
        rules.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        mariaSession = login("maria@orion.test");
    }

    @Test
    void createsARuleForTheLoggedInProfessor() {
        ResponseEntity<RuleResponse> response = post(
                RULES, mariaSession, new CreateRuleRequest(5, LocalTime.of(14, 0), LocalTime.of(16, 0)),
                RuleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().weekday()).isEqualTo(5);
        assertThat(response.getBody().startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(response.getBody().active()).isTrue();
        assertThat(rules.findByProfessorIdAndActiveTrue(maria.getId())).hasSize(1);
    }

    @Test
    void rejectsAWeekdayOutsideTheIsoRange() {
        ResponseEntity<Map> response = post(
                RULES, mariaSession, new CreateRuleRequest(8, LocalTime.of(14, 0), LocalTime.of(16, 0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAnEndTimeThatIsNotAfterTheStartTime() {
        ResponseEntity<Map> response = post(
                RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(16, 0), LocalTime.of(14, 0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("anterior a endTime");
    }

    @Test
    void rejectsMinutesThatAreNotOnTheHour() {
        ResponseEntity<Map> response = post(
                RULES, mariaSession, new CreateRuleRequest(2, LocalTime.of(9, 30), LocalTime.of(11, 30)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains(":00");
    }

    @Test
    void rejectsARuleThatOverlapsAnActiveOneOnTheSameDay() {
        post(RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                RuleResponse.class);

        ResponseEntity<Map> response = post(
                RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(20, 0), LocalTime.of(22, 0)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("solapa");
    }

    @Test
    void acceptsARuleThatOnlyTouchesTheBorderOfAnExistingOne() {
        post(RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                RuleResponse.class);

        // [18:00, 21:00) y [21:00, 22:00) se tocan pero no se solapan: semántica semiabierta.
        ResponseEntity<RuleResponse> response = post(
                RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(21, 0), LocalTime.of(22, 0)),
                RuleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void theSameSlotOnADifferentWeekdayIsNotAnOverlap() {
        post(RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                RuleResponse.class);

        ResponseEntity<RuleResponse> response = post(
                RULES, mariaSession, new CreateRuleRequest(2, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                RuleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aStudentCannotManageAvailabilityRules() {
        Session ana = login("ana@orion.test");

        ResponseEntity<Map> response = get(RULES, ana, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listingOnlyReturnsTheRulesOfTheLoggedInProfessor() {
        rules.save(new AvailabilityRule(juan.getId(), DayOfWeek.TUESDAY, LocalTime.of(15, 0), LocalTime.of(18, 0)));
        post(RULES, mariaSession, new CreateRuleRequest(1, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                RuleResponse.class);

        ResponseEntity<RuleResponse[]> response = get(RULES, mariaSession, RuleResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].weekday()).isEqualTo(1);
    }

    @Test
    void deletesOwnRule() {
        UUID ruleId = post(RULES, mariaSession,
                new CreateRuleRequest(1, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                RuleResponse.class).getBody().id();

        ResponseEntity<Void> response = delete(RULES + "/" + ruleId, mariaSession, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rules.findById(ruleId)).isEmpty();
    }

    @Test
    void deletingAnotherProfessorsRuleReturnsNotFoundAndLeavesItAlone() {
        AvailabilityRule juansRule = rules.save(new AvailabilityRule(
                juan.getId(), DayOfWeek.TUESDAY, LocalTime.of(15, 0), LocalTime.of(18, 0)));

        ResponseEntity<Map> response = delete(RULES + "/" + juansRule.getId(), mariaSession, Map.class);

        // 404 y no 403: un 403 le confirmaría a María que esa regla existe.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rules.findById(juansRule.getId())).isPresent();
    }

    @Test
    void deletingARuleThatDoesNotExistReturnsNotFound() {
        ResponseEntity<Map> response = delete(RULES + "/" + UUID.randomUUID(), mariaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
