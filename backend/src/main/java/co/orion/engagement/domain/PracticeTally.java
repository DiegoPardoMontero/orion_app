package co.orion.engagement.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lo que engagement guarda de cada práctica terminada para sus logros: si fue perfecta, cuántos de
 * escucha acertó y cuántos resolvió al segundo intento. Lo cuenta el evento de la práctica; el set
 * es la llave, así que un evento repetido no suma dos veces.
 */
@Entity
@Table(name = "practice_tallies")
public class PracticeTally {

    @Id
    @Column(name = "practice_set_id", updatable = false)
    private UUID practiceSetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private boolean perfect;

    @Column(name = "listening_correct", nullable = false, updatable = false)
    private short listeningCorrect;

    @Column(name = "second_try_correct", nullable = false, updatable = false)
    private short secondTryCorrect;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected PracticeTally() {
        // exigido por JPA
    }

    public PracticeTally(UUID practiceSetId, UUID userId, boolean perfect, int listeningCorrect,
                         int secondTryCorrect, Instant completedAt) {
        this.practiceSetId = practiceSetId;
        this.userId = userId;
        this.perfect = perfect;
        this.listeningCorrect = (short) listeningCorrect;
        this.secondTryCorrect = (short) secondTryCorrect;
        this.completedAt = completedAt;
    }

    public UUID getPracticeSetId() { return practiceSetId; }
    public UUID getUserId() { return userId; }
    public boolean isPerfect() { return perfect; }
    public int getListeningCorrect() { return listeningCorrect; }
    public int getSecondTryCorrect() { return secondTryCorrect; }
    public Instant getCompletedAt() { return completedAt; }
}
