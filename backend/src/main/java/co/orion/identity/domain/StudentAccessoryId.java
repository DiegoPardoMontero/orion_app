package co.orion.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de {@link StudentAccessory}: una pieza por zona de anclaje. */
public class StudentAccessoryId implements Serializable {

    private UUID userId;
    private String zone;

    public StudentAccessoryId() {
    }

    public StudentAccessoryId(UUID userId, String zone) {
        this.userId = userId;
        this.zone = zone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StudentAccessoryId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(zone, that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, zone);
    }
}
