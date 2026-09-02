package co.orion.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un idioma del catálogo. La PK es el código ISO corto ('EN', 'FR', 'ES'), asignado, no generado. */
@Entity
@Table(name = "languages")
public class Language {

    @Id
    @Column(name = "code", length = 5)
    private String code;

    @Column(name = "name_es", nullable = false, length = 60)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "flag_emoji", length = 8)
    private String flagEmoji;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected Language() {
        // exigido por JPA
    }

    public String getCode() {
        return code;
    }

    public String getNameEs() {
        return nameEs;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getFlagEmoji() {
        return flagEmoji;
    }

    public boolean isActive() {
        return active;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
