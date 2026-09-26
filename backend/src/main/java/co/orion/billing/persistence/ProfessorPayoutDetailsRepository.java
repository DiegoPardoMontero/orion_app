package co.orion.billing.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.ProfessorPayoutDetails;

public interface ProfessorPayoutDetailsRepository extends JpaRepository<ProfessorPayoutDetails, UUID> {
}
