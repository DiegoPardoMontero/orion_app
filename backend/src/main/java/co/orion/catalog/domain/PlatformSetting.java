package co.orion.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un ajuste de negocio (comisión, horas de cancelación…). Clave-valor de texto: el valor se
 * interpreta en el servicio (entero, booleano) según la clave. Vive en la base para poder cambiar
 * una regla con un UPDATE en vez de un despliegue.
 */
@Entity
@Table(name = "platform_settings")
public class PlatformSetting {

    @Id
    @Column(name = "key", length = 60)
    private String key;

    @Column(name = "value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected PlatformSetting() {
        // exigido por JPA
    }

    public void changeValue(String value, UUID updatedBy, Instant now) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
