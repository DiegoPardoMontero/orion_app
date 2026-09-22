package co.orion.support.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.support.domain.CallbackRequest;

public interface CallbackRequestRepository extends JpaRepository<CallbackRequest, UUID> {

    /** Los pendientes primero y, dentro de cada grupo, el más antiguo arriba: es a quien más se hizo esperar. */
    List<CallbackRequest> findTop100ByOrderByAttendedAtDescCreatedAtAsc();
}
