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
 * El consentimiento para tratar la voz. Separado de los términos generales a propósito.
 *
 * <p>La voz es un dato biométrico, y la autorización para tratarlo tiene que ser previa, expresa y
 * <strong>específica</strong> (art. 9 de la Ley 1581 y Decreto 1377). Meterla dentro de «acepto los
 * términos» la viciaría: nadie puede consentir específicamente algo que firmó de paso.
 *
 * <p>Se guardan IP y user-agent porque una autorización sin circunstancias es una afirmación
 * nuestra, no un hecho verificable. Y se puede revocar: al revocarla, los turnos de esa persona se
 * borran en la siguiente corrida del job y se le confirma.
 */
@Entity
@Table(name = "voice_consents")
public class VoiceConsent {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "version", nullable = false, length = 20, updatable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    protected VoiceConsent() {
    }

    public VoiceConsent(UUID userId, String version, Instant acceptedAt,
                        String ipAddress, String userAgent) {
        this.userId = userId;
        this.version = version;
        this.acceptedAt = acceptedAt;
        this.ipAddress = ipAddress;
        this.userAgent = truncar(userAgent);
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public boolean isLive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    /** La columna admite 300; un user-agent largo no puede tumbar un registro de consentimiento. */
    private static String truncar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= 300 ? valor : valor.substring(0, 300);
    }
}
