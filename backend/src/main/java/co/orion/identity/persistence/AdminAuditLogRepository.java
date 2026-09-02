package co.orion.identity.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    List<AdminAuditLog> findByEntityId(UUID entityId);

    List<AdminAuditLog> findByActionAndEntityId(String action, UUID entityId);
}
