package co.orion.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.SocialIdentity;
import co.orion.identity.domain.SocialProvider;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, UUID> {

    Optional<SocialIdentity> findByProviderAndSubject(SocialProvider provider, String subject);

    boolean existsByUserIdAndProvider(UUID userId, SocialProvider provider);
}
