package co.orion.messaging.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.messaging.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByStudentIdAndProfessorId(UUID studentId, UUID professorId);

    /**
     * La bandeja de un usuario, sea el estudiante o el profesor del hilo. Las conversaciones sin
     * mensajes (last_message_at nulo) caen al final; entre las que sí tienen, la más reciente arriba.
     */
    @Query("""
            select c from Conversation c
            where c.studentId = :userId or c.professorId = :userId
            order by c.lastMessageAt desc nulls last, c.createdAt desc
            """)
    List<Conversation> findAllForUser(@Param("userId") UUID userId);
}
