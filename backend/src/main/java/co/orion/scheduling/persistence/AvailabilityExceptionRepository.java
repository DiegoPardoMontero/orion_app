package co.orion.scheduling.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.scheduling.domain.AvailabilityException;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {

    List<AvailabilityException> findByProfessorIdAndExceptionDateBetween(UUID professorId,
                                                                         LocalDate from,
                                                                         LocalDate to);
}
