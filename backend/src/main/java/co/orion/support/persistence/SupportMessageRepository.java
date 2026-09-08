package co.orion.support.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.support.domain.SupportMessage;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
