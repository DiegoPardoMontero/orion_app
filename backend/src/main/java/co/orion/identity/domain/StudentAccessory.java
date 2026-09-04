package co.orion.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Un accesorio equipado en una de las tres zonas de anclaje del avatar (z1 base, z2 centro,
 * z3 corona). La clave compuesta garantiza uno por zona: no hace falta comprobarlo en código.
 */
@Entity
@Table(name = "student_accessories")
@IdClass(StudentAccessoryId.class)
public class StudentAccessory {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "zone", length = 10)
    private String zone;

    @Column(name = "accessory_code", nullable = false, length = 40)
    private String accessoryCode;

    protected StudentAccessory() {
        // exigido por JPA
    }

    public StudentAccessory(UUID userId, String zone, String accessoryCode) {
        this.userId = userId;
        this.zone = zone;
        this.accessoryCode = accessoryCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getZone() {
        return zone;
    }

    public String getAccessoryCode() {
        return accessoryCode;
    }
}
