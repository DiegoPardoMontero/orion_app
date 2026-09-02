package co.orion.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un objetivo de aprendizaje del catálogo ('CONVERSATION', 'BUSINESS'…). PK asignada. */
@Entity
@Table(name = "teaching_goals")
public class TeachingGoal {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "name_es", nullable = false, length = 60)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected TeachingGoal() {
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

    public boolean isActive() {
        return active;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
