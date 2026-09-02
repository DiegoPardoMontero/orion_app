package co.orion.messaging.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.messaging.domain.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * No leídos para un usuario en una conversación: los que no escribió él (los de la contraparte
     * o los del sistema) y que aún no ha leído.
     */
    @Query("""
            select count(m) from Message m
            where m.conversationId = :conversationId
              and m.readAt is null
              and (m.senderId is null or m.senderId <> :userId)
            """)
    int countUnread(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    /** Los mensajes marcados por la política de contacto, para la cola de moderación del admin. */
    List<Message> findByFlaggedReasonIsNotNullOrderByCreatedAtDesc();
}
