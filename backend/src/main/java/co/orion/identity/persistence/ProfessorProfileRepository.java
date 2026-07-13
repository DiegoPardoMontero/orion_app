package co.orion.identity.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.identity.domain.ProfessorProfile;

public interface ProfessorProfileRepository extends JpaRepository<ProfessorProfile, UUID> {
}
