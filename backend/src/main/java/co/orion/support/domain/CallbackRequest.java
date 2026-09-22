package co.orion.support.domain;

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
 * Alguien que prefiere que una persona de la academia le escriba antes que hablar con Meissa.
 *
 * <p>No tiene cuenta ni la necesita: un nombre, un WhatsApp y la autorización para usarlo con esa
 * sola finalidad. Queda pendiente hasta que alguien del equipo lo atiende y lo marca.
 */
@Entity
@Table(name = "callback_requests")
public class CallbackRequest {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 60, updatable = false)
    private String firstName;

    @Column(name = "whatsapp", nullable = false, length = 20, updatable = false)
    private String whatsapp;

    @Column(name = "consent_at", nullable = false, updatable = false)
    private Instant consentAt;

    @Column(name = "consent_ip", length = 45, updatable = false)
    private String consentIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "attended_at")
    private Instant attendedAt;

    @Column(name = "attended_by")
    private UUID attendedBy;

    protected CallbackRequest() {
    }

    public CallbackRequest(String firstName, String whatsapp, Instant now, String ip) {
        this.firstName = firstName.trim();
        this.whatsapp = whatsapp;
        this.consentAt = now;
        this.consentIp = ip;
        this.createdAt = now;
    }

    /** Idempotente: marcar dos veces no cambia quién lo atendió primero ni cuándo. */
    public void attend(UUID adminId, Instant now) {
        if (attendedAt == null) {
            this.attendedAt = now;
            this.attendedBy = adminId;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAttendedAt() {
        return attendedAt;
    }
}
