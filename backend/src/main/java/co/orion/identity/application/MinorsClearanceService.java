package co.orion.identity.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.api.DocumentView;
import co.orion.identity.api.MinorsClearanceView;
import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.legal.application.LegalDocumentService;
import co.orion.legal.domain.LegalDocumentCode;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.time.BusinessZone;

/**
 * Quién puede dar clase a un adolescente (Bloque 12, decisión de Pardo del 14/10/2026): el profe acepta
 * el código de conducta y sube la consulta del Registro de inhabilidades (Ley 1918 de 2018) y sus
 * antecedentes; el admin los revisa y enciende la habilitación por un año. Vencida o apagada, el profe
 * no aparece para un perfil de adolescente y la reserva lo rechaza.
 */
@Service
public class MinorsClearanceService {

    private final ProfessorProfileRepository profiles;
    private final TeacherDocumentRepository documents;
    private final LegalDocumentService legal;
    private final AdminAuditService audit;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MinorsClearanceService(ProfessorProfileRepository profiles,
                                  TeacherDocumentRepository documents,
                                  LegalDocumentService legal,
                                  AdminAuditService audit,
                                  UserRepository users,
                                  ApplicationEventPublisher events,
                                  Clock clock) {
        this.profiles = profiles;
        this.documents = documents;
        this.legal = legal;
        this.audit = audit;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MinorsClearanceView view(UUID professorId) {
        return view(profesor(professorId));
    }

    /** La de cada profe de la lista, para el admin. */
    @Transactional(readOnly = true)
    public Map<UUID, MinorsClearanceView> viewsOf(Collection<UUID> professorIds) {
        Map<UUID, MinorsClearanceView> vistas = new HashMap<>();
        for (ProfessorProfile profile : profiles.findAllById(professorIds)) {
            vistas.put(profile.getUserId(), view(profile));
        }
        return vistas;
    }

    /** El profe acepta el código de conducta para clases con menores, con constancia. */
    @Transactional
    public MinorsClearanceView acceptConduct(User professor, String ip, String userAgent) {
        ProfessorProfile profile = profesor(professor.getId());
        legal.record(professor.getId(), LegalDocumentCode.MINORS_CODE_OF_CONDUCT, ip, userAgent);
        return view(profile);
    }

    /**
     * El admin la enciende por un año desde hoy. Solo exige el código de conducta, que es del propio
     * profe y que el admin no puede aceptar por él; los dos certificados se ven pero no bloquean (Pardo,
     * 15/10/2026: «puedo habilitarlo desde el administrador sin que esto lo bloquee»). El profe se entera
     * por correo y por su campana.
     */
    @Transactional
    public MinorsClearanceView grant(UUID professorId, UUID adminId) {
        ProfessorProfile profile = profesor(professorId);
        MinorsClearanceView antes = view(profile);
        if (!antes.readyToClear()) {
            throw new BusinessRuleViolationException(
                    "Todavía no se puede habilitar: el profe no ha aceptado el código de conducta para clases con menores.");
        }
        profile.clearForMinors(hoy(), adminId, clock.instant());
        profiles.save(profile);
        audit.record(adminId, "GRANT_MINORS_CLEARANCE", "professor_profile", professorId,
                "{\"until\":\"" + profile.getMinorsClearedUntil() + "\"}");
        users.findById(professorId).ifPresent(u -> events.publishEvent(new MinorsClearanceGrantedEvent(
                professorId, u.getEmail(), primerNombre(u.getFullName()), profile.getMinorsClearedUntil())));
        return view(profile);
    }

    private static String primerNombre(String fullName) {
        return fullName == null ? "" : fullName.trim().split("\\s+")[0];
    }

    @Transactional
    public void revoke(UUID professorId, UUID adminId) {
        ProfessorProfile profile = profesor(professorId);
        if (profile.getMinorsClearedUntil() != null) {
            profile.revokeMinorsClearance();
            profiles.save(profile);
            audit.record(adminId, "REVOKE_MINORS_CLEARANCE", "professor_profile", professorId, "{}");
        }
    }

    /** La comprobación del servidor: la usan el buscador, la reserva y el aula. */
    @Transactional(readOnly = true)
    public boolean isCleared(UUID professorId) {
        return profiles.findById(professorId).map(p -> p.isClearedForMinors(hoy())).orElse(false);
    }

    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(BusinessZone.BOGOTA));
    }

    public MinorsClearanceView view(ProfessorProfile profile) {
        UUID id = profile.getUserId();
        boolean conducta = legal.aceptoLaVigente(id, LegalDocumentCode.MINORS_CODE_OF_CONDUCT);
        boolean registro = documents.existsByUserIdAndDocType(id, DocumentType.REGISTRY_CHECK);
        boolean antecedentes = documents.existsByUserIdAndDocType(id, DocumentType.BACKGROUND_CHECK);
        List<String> falta = new ArrayList<>();
        if (!conducta) {
            falta.add("aceptar el código de conducta");
        }
        if (!registro) {
            falta.add("la consulta del Registro de inhabilidades");
        }
        if (!antecedentes) {
            falta.add("el certificado de antecedentes");
        }
        List<DocumentView> subidos = documents.findByUserIdAndDocTypeIn(id, TeacherDocumentService.MINORS_TYPES).stream()
                .map(DocumentView::of).toList();
        return new MinorsClearanceView(profile.isClearedForMinors(hoy()), profile.getMinorsClearedUntil(),
                profile.getMinorsClearedAt(), conducta, registro, antecedentes, List.copyOf(falta), subidos);
    }

    private ProfessorProfile profesor(UUID professorId) {
        return profiles.findById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
    }
}
