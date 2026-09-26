package co.orion.billing.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.AdminAuditService;
import co.orion.identity.application.DocumentStorage;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * El certificado anual de ingresos recibidos para terceros (brief de liquidaciones, paso 6). Lo firma
 * un contador público; el admin sube el PDF firmado y el profe lo descarga. Se guarda privado, con
 * URL firmada de pocos minutos, como los documentos de la postulación.
 */
@Service
public class PayoutCertificates {

    private static final Duration URL_TTL = Duration.ofMinutes(5);
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final DocumentStorage storage;
    private final AdminAuditService audit;

    public PayoutCertificates(JdbcTemplate jdbc, DocumentStorage storage, AdminAuditService audit) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.audit = audit;
    }

    /** Sube (o reemplaza) el PDF firmado de un profe en un año. */
    @Transactional
    public void upload(UUID professorId, int year, byte[] pdf, String contentType, String fileName, UUID adminId) {
        if (pdf == null || pdf.length == 0) {
            throw new BusinessRuleViolationException("El archivo está vacío");
        }
        if (pdf.length > MAX_BYTES) {
            throw new BusinessRuleViolationException("El PDF pesa más de 10 MB");
        }
        if (!"application/pdf".equals(contentType)) {
            throw new BusinessRuleViolationException("El certificado firmado tiene que ser un PDF");
        }
        String key = storage.upload(pdf, contentType, professorId, "certificado-" + year + ".pdf");
        jdbc.update("""
                insert into payout_certificates (professor_id, year, storage_key, content_type, uploaded_at, uploaded_by)
                values (?, ?, ?, ?, now(), ?)
                on conflict (professor_id, year) do update
                   set storage_key = excluded.storage_key, content_type = excluded.content_type,
                       uploaded_at = excluded.uploaded_at, uploaded_by = excluded.uploaded_by
                """, professorId, year, key, contentType, adminId);
        audit.record(adminId, "UPLOAD_PAYOUT_CERTIFICATE", "USER", professorId, "{\"year\":" + year + "}");
    }

    /** Los años con certificado subido. Mientras no haya ninguno, el profe no ve nada (nada de UI muerta). */
    @Transactional(readOnly = true)
    public List<Uploaded> of(UUID professorId) {
        return jdbc.query("select year, uploaded_at from payout_certificates where professor_id = ? order by year desc",
                (rs, i) -> new Uploaded(rs.getInt("year"), rs.getTimestamp("uploaded_at").toInstant()), professorId);
    }

    /** La URL firmada para descargarlo. Solo existe si se subió. */
    @Transactional(readOnly = true)
    public String signedUrl(UUID professorId, int year) {
        return jdbc.query("select storage_key, content_type from payout_certificates where professor_id = ? and year = ?",
                        (rs, i) -> storage.signedUrl(rs.getString("storage_key"), rs.getString("content_type"), URL_TTL),
                        professorId, year)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No hay certificado de ese año"));
    }

    public record Uploaded(int year, Instant uploadedAt) {
    }
}
