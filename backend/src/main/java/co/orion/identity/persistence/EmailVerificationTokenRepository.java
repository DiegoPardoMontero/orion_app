package co.orion.identity.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.identity.domain.EmailVerificationToken;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Invalida los enlaces anteriores sin borrar sus filas.
     *
     * <p>Marcarlos en vez de borrarlos no es un detalle de estilo: el freno al reenvío cuenta los
     * envíos de la última hora, y si cada envío borrara los anteriores el contador nunca pasaría
     * de uno y el freno no frenaría nada. Además deja el rastro de cuántas veces se pidió, que es
     * justo lo que hay que poder mirar cuando alguien dice que el correo no le llega.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.usedAt is null")
    void invalidateAllFor(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Cuántos envíos lleva en la ventana. Es el freno al reenvío: un botón no es un altavoz. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
