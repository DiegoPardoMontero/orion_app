package co.orion.catalog.domain;

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
 * Un cambio de ajuste: de qué a qué, quién y cuándo.
 *
 * <p>No se edita ni se borra. `platform_settings` ya guardaba el último autor, pero no el valor
 * anterior — y cuando la comisión aparezca en 30 % un lunes, la pregunta va a ser «¿desde cuánto?».
 */
@Entity
@Table(name = "platform_setting_changes")
public class PlatformSettingChange {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "key", nullable = false, updatable = false, length = 60)
    private String key;

    @Column(name = "old_value", updatable = false)
    private String oldValue;

    @Column(name = "new_value", nullable = false, updatable = false)
    private String newValue;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false, insertable = false)
    private Instant changedAt;

    protected PlatformSettingChange() {
        // exigido por JPA
    }

    public PlatformSettingChange(String key, String oldValue, String newValue, UUID changedBy) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
    }

    public String getKey() {
        return key;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
