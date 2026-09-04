package co.orion.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Una pieza del avatar: marco (órbita), paleta, cielo (fondo) o accesorio.
 *
 * <p>O es inicial o tiene un logro que lo desbloquea — lo garantiza un CHECK, así que ninguna pieza
 * queda inalcanzable por un olvido al sembrar el catálogo.
 */
@Entity
@Table(name = "cosmetics")
@IdClass(CosmeticId.class)
public class Cosmetic {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20)
    private CosmeticKind kind;

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Solo los accesorios: z1 base, z2 centro, z3 corona. */
    @Column(name = "zone", length = 10)
    private String zone;

    @Column(name = "unlock_achievement", length = 60)
    private String unlockAchievement;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPiece;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected Cosmetic() {
        // exigido por JPA
    }

    public CosmeticKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getZone() {
        return zone;
    }

    public String getUnlockAchievement() {
        return unlockAchievement;
    }

    public boolean isDefaultPiece() {
        return defaultPiece;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
