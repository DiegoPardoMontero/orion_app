package co.orion.admin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.admin.api.DashboardResponse;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.application.JobRunRegistry;
import co.orion.lifecycle.persistence.DisputeRepository;
import co.orion.reputation.persistence.ProfessorSanctionRepository;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.reputation.domain.SanctionState;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.RescheduleRequestRepository;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.shared.time.BusinessZone;

/**
 * Arma el tablero del admin. Una consulta por cifra y ninguna cacheada: a este volumen es barato, y
 * un tablero que miente es peor que no tener tablero.
 */
@Service
public class DashboardService {

    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final TeacherApplicationRepository applications;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final StudentCreditRepository credits;
    private final DisputeRepository disputes;
    private final RescheduleRequestRepository reschedules;
    private final ProfessorSanctionRepository sanctions;
    private final ReviewRepository reviews;
    private final JobRunRegistry jobs;
    private final Clock clock;

    public DashboardService(UserRepository users,
                            ProfessorProfileRepository profiles,
                            TeacherApplicationRepository applications,
                            BookingRepository bookings,
                            PaymentRepository payments,
                            StudentCreditRepository credits,
                            DisputeRepository disputes,
                            RescheduleRequestRepository reschedules,
                            ProfessorSanctionRepository sanctions,
                            ReviewRepository reviews,
                            JobRunRegistry jobs,
                            Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.applications = applications;
        this.bookings = bookings;
        this.payments = payments;
        this.credits = credits;
        this.disputes = disputes;
        this.reschedules = reschedules;
        this.sanctions = sanctions;
        this.reviews = reviews;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        Instant now = clock.instant();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status.name(), bookings.countByStatus(status));
        }

        var people = new DashboardResponse.People(
                users.countByRole(UserRole.STUDENT),
                users.countByRole(UserRole.PROFESSOR),
                users.countByRole(UserRole.ADMIN),
                profiles.countByPublishedTrue(),
                applications.countByStatus(ApplicationStatus.PENDING_REVIEW)
                        + applications.countByStatus(ApplicationStatus.UNDER_REVIEW));

        var lessons = new DashboardResponse.Lessons(
                byStatus,
                bookings.countByCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(7))),
                bookings.selfServicePercentage());

        var money = new DashboardResponse.Money(
                payments.sumEarningsByStatusAllProfessors(PaymentStatus.PAID),
                payments.sumEarningsByStatusAllProfessors(PaymentStatus.RELEASED),
                payments.sumTransferred(),
                payments.sumCommissionOn(List.of(PaymentStatus.PAID, PaymentStatus.RELEASED)),
                credits.sumOutstanding(now));

        var attention = new DashboardResponse.Attention(
                disputes.findBookingIdsWithOpenDispute().size(),
                payments.countNeedingReview(),
                sanctions.findByStateOrderByCreatedAtDesc(SanctionState.PROPOSED).size(),
                reschedules.countPending(),
                reviews.countReported());

        List<DashboardResponse.JobHealth> jobHealth = jobs.all().stream()
                .map(run -> new DashboardResponse.JobHealth(
                        run.job(), run.at().atZone(BusinessZone.BOGOTA), run.ok(), run.detail()))
                .toList();

        return new DashboardResponse(people, lessons, money, attention, jobHealth);
    }
}
