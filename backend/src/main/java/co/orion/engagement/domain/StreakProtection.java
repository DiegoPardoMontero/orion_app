package co.orion.engagement.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una semana vacía que no cortó la racha.
 *
 * <p>Se concede una al mes, y lo garantiza el índice único {@code (user_id, granted_for)}: no hay
 * un contador que consultar ni un {@code if} que se pueda olvidar. Se consume al evaluar la racha,
 * no al reservar, así que es retroactiva y silenciosa — el estudiante se entera cuando ve su racha
 * intacta.
 */
@Entity
@Table(name = "streak_protections")
public class StreakProtection {

    @Id
    @org.hibernate.annotations.Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** El lunes de la semana protegida. */
    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    /** Primer día del mes al que pertenece la protección: es la clave de «una al mes». */
    @Column(name = "granted_for", nullable = false, updatable = false)
    private LocalDate grantedFor;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected StreakProtection() {
        // exigido por JPA
    }

    public StreakProtection(UUID userId, LocalDate weekStart) {
        this.userId = userId;
        this.weekStart = weekStart;
        this.grantedFor = weekStart.withDayOfMonth(1);
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDate getGrantedFor() {
        return grantedFor;
    }
}
