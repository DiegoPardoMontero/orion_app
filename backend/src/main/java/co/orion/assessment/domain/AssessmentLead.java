package co.orion.assessment.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Quien hace el diagnóstico sin tener cuenta: un nombre de pila, sus dos declaraciones y la llave
 * de su dispositivo.
 *
 * <p>No es una cuenta a medias. No tiene correo ni contraseña, no reserva ni paga: existe para que
 * alguien pueda hablar con Meissa sin registrarse antes (Pardo, 22/09/2026). Cuando crea su cuenta
 * o entra, se <em>reclama</em> y sus diagnósticos pasan a ella. Si nunca lo hace, el job de
 * retención lo borra, y con él lo que dijo.
 *
 * <p>La llave se guarda como hash: con una copia de la base no se puede suplantar a nadie.
 */
@Entity
@Table(name = "assessment_leads")
public class AssessmentLead {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 60, updatable = false)
    private String firstName;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "adult_declared_at", nullable = false, updatable = false)
    private Instant adultDeclaredAt;

    @Column(name = "consent_version", nullable = false, length = 20, updatable = false)
    private String consentVersion;

    @Column(name = "consent_accepted_at", nullable = false, updatable = false)
    private Instant consentAcceptedAt;

    @Column(name = "consent_ip", length = 45, updatable = false)
    private String consentIp;

    @Column(name = "consent_user_agent", length = 300, updatable = false)
    private String consentUserAgent;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssessmentLead() {
    }

    public AssessmentLead(String firstName, String tokenHash, String consentVersion, Instant now,
                          String ip, String userAgent) {
        this.firstName = firstName.trim();
        this.tokenHash = tokenHash;
        this.adultDeclaredAt = now;
        this.consentVersion = consentVersion;
        this.consentAcceptedAt = now;
        this.consentIp = ip;
        this.consentUserAgent = userAgent == null || userAgent.length() <= 300
                ? userAgent : userAgent.substring(0, 300);
        this.createdAt = now;
    }

    public void claim(UUID userId, Instant now) {
        this.claimedBy = userId;
        this.claimedAt = now;
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public Instant getConsentAcceptedAt() {
        return consentAcceptedAt;
    }

    public String getConsentIp() {
        return consentIp;
    }

    public String getConsentUserAgent() {
        return consentUserAgent;
    }

    public UUID getClaimedBy() {
        return claimedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
