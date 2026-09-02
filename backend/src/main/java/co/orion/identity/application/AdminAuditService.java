package co.orion.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.AdminAuditLog;
import co.orion.identity.persistence.AdminAuditLogRepository;

/** Escribe la bitácora de acciones sensibles del admin. {@code detail} es un JSON simple (String). */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository logs;

    public AdminAuditService(AdminAuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(UUID actorId, String action, String entityType, UUID entityId, String detailJson) {
        logs.save(new AdminAuditLog(actorId, action, entityType, entityId, detailJson));
    }
}
