package co.orion.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aceptación versionada de un documento legal (hoy, el Teacher Agreement). Con IP y user-agent. */
@Entity
@Table(name = "agreement_acceptances")
@EntityListeners(AuditingEntityListener.class)
public class AgreementAcceptance {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "document_code", nullable = false, length = 40)
    private String documentCode;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @CreatedDate
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    protected AgreementAcceptance() {
        // exigido por JPA
    }

    public AgreementAcceptance(UUID userId, String documentCode, String version,
                               String ipAddress, String userAgent) {
        this.userId = userId;
        this.documentCode = documentCode;
        this.version = version;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDocumentCode() {
        return documentCode;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
