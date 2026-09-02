package co.orion.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.api.AdminApplicationDetail;
import co.orion.identity.api.AdminApplicationSummary;
import co.orion.identity.api.ApplicationEventView;
import co.orion.identity.api.DocumentView;
import co.orion.identity.api.PagedApplications;
import co.orion.identity.api.TeacherApplicationView;
import co.orion.identity.domain.ApplicationEventType;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.TeacherApplicationEvent;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.AgreementAcceptanceRepository;
import co.orion.identity.persistence.ProfessorGoalRepository;
import co.orion.identity.persistence.ProfessorLanguageLevelRepository;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationEventRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * El corazón del Bloque 2: la máquina de estados de las postulaciones y la revisión del admin.
 * Nadie enseña en Orión sin pasar por aquí, y cada transición queda registrada (evento + bitácora).
 */
@Service
public class TeacherApplicationService {

    public static final String TEACHER_AGREEMENT = "TEACHER_AGREEMENT";
    private static final String AGREEMENT_VERSION = "1.0";
    private static final int MIN_NOTE_LENGTH = 10;
    private static final List<ApplicationStatus> OPEN = List.of(
            ApplicationStatus.DRAFT, ApplicationStatus.PENDING_REVIEW,
            ApplicationStatus.UNDER_REVIEW, ApplicationStatus.CHANGES_REQUESTED);

    private final TeacherApplicationRepository applications;
    private final TeacherApplicationEventRepository events;
    private final TeacherDocumentRepository documents;
    private final AgreementAcceptanceRepository agreements;
    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final ProfessorLanguageRepository languages;
    private final ProfessorLanguageLevelRepository levels;
    private final ProfessorGoalRepository goals;
    private final ProfessorProfileService profileService;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public TeacherApplicationService(TeacherApplicationRepository applications,
                                     TeacherApplicationEventRepository events,
                                     TeacherDocumentRepository documents,
                                     AgreementAcceptanceRepository agreements,
                                     UserRepository users,
                                     ProfessorProfileRepository profiles,
                                     ProfessorLanguageRepository languages,
                                     ProfessorLanguageLevelRepository levels,
                                     ProfessorGoalRepository goals,
                                     ProfessorProfileService profileService,
                                     AdminAuditService audit,
                                     ApplicationEventPublisher publisher,
                                     Clock clock) {
        this.applications = applications;
        this.events = events;
        this.documents = documents;
        this.agreements = agreements;
        this.users = users;
        this.profiles = profiles;
        this.languages = languages;
        this.levels = levels;
        this.goals = goals;
        this.profileService = profileService;
        this.audit = audit;
        this.publisher = publisher;
        this.clock = clock;
    }

    // --- Aspirante ---

    /** Crea (o devuelve) la postulación viva del usuario. Idempotente: nunca abre dos a la vez. */
    @Transactional
    public TeacherApplicationView getOrCreateDraft(UUID userId) {
        TeacherApplication application = openApplicationOf(userId).orElseGet(() -> {
            TeacherApplication created = applications.saveAndFlush(new TeacherApplication(userId));
            events.save(new TeacherApplicationEvent(
                    created.getId(), ApplicationEventType.CREATED, userId, null));
            return created;
        });
        return toView(userId, application);
    }

    @Transactional(readOnly = true)
    public TeacherApplicationView getMine(UUID userId) {
        TeacherApplication application = applications.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una postulación"));
        return toView(userId, application);
    }

    @Transactional
    public void acceptAgreement(UUID userId, String code, String ip, String userAgent) {
        String documentCode = code == null ? "" : code.trim().toUpperCase();
        if (!TEACHER_AGREEMENT.equals(documentCode)) {
            throw new ResourceNotFoundException("Documento no encontrado");
        }
        if (agreements.existsByUserIdAndDocumentCode(userId, documentCode)) {
            return; // ya aceptado: idempotente (el índice único lo respalda)
        }
        agreements.save(new co.orion.identity.domain.AgreementAcceptance(
                userId, documentCode, AGREEMENT_VERSION, ip, userAgent));
    }

    @Transactional
    public TeacherApplicationView submit(UUID userId) {
        TeacherApplication application = openApplicationOf(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una postulación por enviar"));

        List<String> missing = missingRequirements(userId);
        if (!missing.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Faltan requisitos para enviar tu postulación", missing);
        }

        Instant now = clock.instant();
        // DRAFT → SUBMITTED; CHANGES_REQUESTED → RESUBMITTED. Cualquier otro estado: la entidad lanza 409.
        if (application.getStatus() == ApplicationStatus.CHANGES_REQUESTED) {
            application.markResubmitted(now);
            events.save(new TeacherApplicationEvent(
                    application.getId(), ApplicationEventType.RESUBMITTED, userId, null));
        } else {
            application.submit(now);
            events.save(new TeacherApplicationEvent(
                    application.getId(), ApplicationEventType.SUBMITTED, userId, null));
        }
        applications.save(application);
        return toView(userId, application);
    }

    // --- Admin ---

    @Transactional(readOnly = true)
    public PagedApplications list(String statusName, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Order.desc("createdAt")));
        Page<TeacherApplication> found = statusName == null || statusName.isBlank()
                ? applications.findAll(pageable)
                : applications.findByStatus(parseStatus(statusName), pageable);

        List<UUID> userIds = found.getContent().stream().map(TeacherApplication::getUserId).toList();
        Map<UUID, User> byId = userIds.isEmpty() ? Map.of()
                : users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminApplicationSummary> content = found.getContent().stream()
                .map(a -> summary(a, byId.get(a.getUserId())))
                .toList();
        return new PagedApplications(content, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminApplicationDetail detail(UUID applicationId) {
        TeacherApplication application = require(applicationId);
        User user = users.findById(application.getUserId()).orElse(null);
        List<DocumentView> docs = documents.findByUserId(application.getUserId()).stream()
                .map(DocumentView::of).toList();
        List<ApplicationEventView> history = events
                .findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(ApplicationEventView::of).toList();
        return new AdminApplicationDetail(
                summary(application, user),
                profileService.getOwnProfile(application.getUserId()),
                docs,
                history);
    }

    @Transactional
    public void startReview(UUID applicationId, UUID adminId) {
        TeacherApplication application = require(applicationId);
        assertNotSelf(application, adminId);
        application.startReview();
        events.save(new TeacherApplicationEvent(
                applicationId, ApplicationEventType.REVIEW_STARTED, adminId, null));
        applications.save(application);
    }

    @Transactional
    public void approve(UUID applicationId, UUID adminId) {
        TeacherApplication application = require(applicationId);
        assertNotSelf(application, adminId);
        application.approve(adminId, "Aprobada", clock.instant());
        events.save(new TeacherApplicationEvent(
                applicationId, ApplicationEventType.APPROVED, adminId, "Aprobada"));
        applications.save(application);
        audit.record(adminId, "APPROVE_APPLICATION", "teacher_application", applicationId,
                "{\"status\":\"APPROVED\"}");
        notify(application.getUserId(), TeacherApplicationDecidedEvent.Decision.APPROVED, null);
    }

    @Transactional
    public void reject(UUID applicationId, UUID adminId, String note) {
        String reason = requireNote(note);
        TeacherApplication application = require(applicationId);
        assertNotSelf(application, adminId);
        application.reject(adminId, reason, clock.instant());
        events.save(new TeacherApplicationEvent(
                applicationId, ApplicationEventType.REJECTED, adminId, reason));
        applications.save(application);
        audit.record(adminId, "REJECT_APPLICATION", "teacher_application", applicationId,
                "{\"status\":\"REJECTED\"}");
        notify(application.getUserId(), TeacherApplicationDecidedEvent.Decision.REJECTED, reason);
    }

    @Transactional
    public void requestChanges(UUID applicationId, UUID adminId, String note) {
        String reason = requireNote(note);
        TeacherApplication application = require(applicationId);
        assertNotSelf(application, adminId);
        application.requestChanges(adminId, reason, clock.instant());
        events.save(new TeacherApplicationEvent(
                applicationId, ApplicationEventType.CHANGES_REQUESTED, adminId, reason));
        applications.save(application);
        audit.record(adminId, "REQUEST_CHANGES", "teacher_application", applicationId,
                "{\"status\":\"CHANGES_REQUESTED\"}");
        notify(application.getUserId(), TeacherApplicationDecidedEvent.Decision.CHANGES_REQUESTED, reason);
    }

    // --- helpers ---

    private void notify(UUID userId, TeacherApplicationDecidedEvent.Decision decision, String note) {
        users.findById(userId).ifPresent(user ->
                publisher.publishEvent(new TeacherApplicationDecidedEvent(user.getEmail(), decision, note)));
    }

    private void assertNotSelf(TeacherApplication application, UUID adminId) {
        if (application.getUserId().equals(adminId)) {
            throw new ForbiddenException("No puedes revisar tu propia postulación");
        }
    }

    private String requireNote(String note) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() < MIN_NOTE_LENGTH) {
            throw new BusinessRuleViolationException(
                    "El motivo debe tener al menos " + MIN_NOTE_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private TeacherApplication require(UUID applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación no encontrada"));
    }

    private java.util.Optional<TeacherApplication> openApplicationOf(UUID userId) {
        return applications.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, OPEN);
    }

    /** Requisitos que faltan para poder enviar a revisión. Devuelve la lista completa, no el primero. */
    private List<String> missingRequirements(UUID userId) {
        List<String> missing = new ArrayList<>();

        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (isBlank(user.getPhotoUrl())) {
            missing.add("photo");
        }

        ProfessorProfile profile = profiles.findByIdWithUser(userId).orElse(null);
        if (profile == null || isBlank(profile.getBio())) {
            missing.add("bio");
        }

        boolean hasLanguage = !languages.findByProfessorId(userId).isEmpty();
        boolean hasLevel = !levels.findByProfessorId(userId).isEmpty();
        if (!hasLanguage || !hasLevel) {
            missing.add("language");
        }

        if (goals.findByProfessorId(userId).isEmpty()) {
            missing.add("goal");
        }

        if (!documents.existsByUserIdAndDocType(userId, DocumentType.CV)) {
            missing.add("cv");
        }

        if (!agreements.existsByUserIdAndDocumentCode(userId, TEACHER_AGREEMENT)) {
            missing.add("agreement");
        }

        // TODO: exigir email verificado cuando exista Fase B.
        return missing;
    }

    private TeacherApplicationView toView(UUID userId, TeacherApplication application) {
        List<DocumentView> docs = documents.findByUserId(userId).stream()
                .map(DocumentView::of).toList();
        // La lista de faltantes solo tiene sentido mientras la postulación siga viva.
        List<String> missing = application.getStatus().isTerminal()
                ? List.of() : missingRequirements(userId);
        boolean agreementAccepted = agreements.existsByUserIdAndDocumentCode(userId, TEACHER_AGREEMENT);
        return new TeacherApplicationView(
                application.getId(),
                application.getStatus().name(),
                application.getSubmittedAt(),
                application.getDecisionNote(),
                agreementAccepted,
                missing,
                docs);
    }

    private AdminApplicationSummary summary(TeacherApplication a, User user) {
        return new AdminApplicationSummary(
                a.getId(),
                a.getUserId(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getEmail(),
                a.getStatus().name(),
                a.getSubmittedAt(),
                a.getCreatedAt());
    }

    private ApplicationStatus parseStatus(String name) {
        try {
            return ApplicationStatus.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Estado de postulación inválido: " + name);
        }
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
