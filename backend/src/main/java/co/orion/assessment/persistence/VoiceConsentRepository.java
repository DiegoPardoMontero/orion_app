package co.orion.assessment.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.assessment.domain.VoiceConsent;

public interface VoiceConsentRepository extends JpaRepository<VoiceConsent, UUID> {

    /** El último que dio, revocado o no. Vigente = existe y no está revocado. */
    Optional<VoiceConsent> findFirstByUserIdOrderByAcceptedAtDesc(UUID userId);

    /** Quienes lo revocaron: sus transcripciones se borran en la siguiente corrida. */
    List<VoiceConsent> findByRevokedAtIsNotNull();
}
