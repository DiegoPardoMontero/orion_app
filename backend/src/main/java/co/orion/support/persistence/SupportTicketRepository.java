package co.orion.support.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import co.orion.support.domain.SupportTicket;
import co.orion.support.domain.TicketStatus;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Optional<SupportTicket> findByCode(String code);

    boolean existsByCode(String code);

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);

    long countByStatus(TicketStatus status);

    /**
     * La bandeja del administrador: lo que vence antes va primero, y lo que no tiene plazo legal
     * va al final. Ordenar por fecha de creación pondría por delante un «no me carga la foto» de
     * hace tres días frente a un reclamo de habeas data que vence mañana.
     */
    @Query("""
            select t from SupportTicket t
            where t.status <> co.orion.support.domain.TicketStatus.CLOSED
            order by case when t.dueAt is null then 1 else 0 end, t.dueAt asc, t.createdAt asc
            """)
    List<SupportTicket> findAbiertosPorUrgencia();
}
