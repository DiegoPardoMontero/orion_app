package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
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

/**
 * La autorización del representante legal para tratar los datos de un adolescente (Ley 1581 de 2012,
 * art. 7; Decreto 1377 de 2013, art. 12, hoy en el Decreto 1074 de 2015).
 *
 * <p>Es la constancia: quién autorizó, qué versión del texto, cuándo, desde dónde, y que el adolescente
 * fue escuchado antes. Revocarla la marca, no la borra: que existió es lo que protege a todos. Una
 * sola viva por perfil (índice parcial de la V83).
 */
@Entity
@Table(name = "guardian_authorizations")
@EntityListeners(AuditingEntityListener.class)
public class GuardianAuthorization {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "adolescent_profile_id", nullable = false, updatable = false)
    private UUID adolescentProfileId;

    @Column(name = "guardian_user_id", nullable = false, updatable = false)
    private UUID guardianUserId;

    @Column(name = "document_version", nullable = false, updatable = false, length = 20)
    private String documentVersion;

    @Column(name = "heard_confirmed", nullable = false, updatable = false)
    private boolean heardConfirmed;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 300)
    private String userAgent;

    @CreatedDate
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected GuardianAuthorization() {
    }

    public GuardianAuthorization(UUID adolescentProfileId, UUID guardianUserId, String documentVersion,
                                 boolean heardConfirmed, String ipAddress, String userAgent) {
        this.adolescentProfileId = Objects.requireNonNull(adolescentProfileId, "adolescentProfileId");
        this.guardianUserId = Objects.requireNonNull(guardianUserId, "guardianUserId");
        this.documentVersion = Objects.requireNonNull(documentVersion, "documentVersion");
        this.heardConfirmed = heardConfirmed;
        this.ipAddress = ipAddress == null ? null : ipAddress.length() > 45 ? ipAddress.substring(0, 45) : ipAddress;
        this.userAgent = userAgent == null ? null : userAgent.length() > 300 ? userAgent.substring(0, 300) : userAgent;
    }

    public void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(when, "when");
        }
    }

    public boolean isAlive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdolescentProfileId() {
        return adolescentProfileId;
    }

    public UUID getGuardianUserId() {
        return guardianUserId;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public boolean isHeardConfirmed() {
        return heardConfirmed;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
