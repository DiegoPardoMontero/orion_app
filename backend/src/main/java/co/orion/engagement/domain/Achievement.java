package co.orion.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un logro del catálogo. Es DATO, no código: un logro nuevo de un tipo que ya existe es un INSERT,
 * y por eso el criterio se guarda como tipo + parámetros en vez de como una clase por logro.
 */
@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @Column(name = "code", length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "family", nullable = false, length = 20)
    private AchievementFamily family;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** El texto del diseño, con la voz de marca. Es lo que lee el estudiante. */
    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "criteria_type", nullable = false, length = 40)
    private CriteriaType criteriaType;

    /** Parámetros del criterio, en JSON. Vacío para los que no necesitan ninguno. */
    @Column(name = "criteria_params", nullable = false, columnDefinition = "jsonb")
    private String criteriaParams = "{}";

    @Column(name = "target", nullable = false)
    private int target;

    /** El escalón visual: 1 estrella · 2 con halo · 3 con halo y rayos. */
    @Column(name = "glow", nullable = false)
    private short glow;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Achievement() {
        // exigido por JPA
    }

    public String getCode() {
        return code;
    }

    public AchievementFamily getFamily() {
        return family;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CriteriaType getCriteriaType() {
        return criteriaType;
    }

    public String getCriteriaParams() {
        return criteriaParams;
    }

    public int getTarget() {
        return target;
    }

    public short getGlow() {
        return glow;
    }

    public int getPoints() {
        return points;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
