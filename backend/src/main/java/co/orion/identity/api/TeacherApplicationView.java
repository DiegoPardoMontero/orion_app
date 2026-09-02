package co.orion.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La postulación vista por el propio aspirante: estado, feedback del admin, lista de requisitos que
 * aún faltan para poder enviarla, y sus documentos.
 */
public record TeacherApplicationView(
        UUID id,
        String status,
        Instant submittedAt,
        String decisionNote,
        boolean agreementAccepted,
        List<String> missing,
        List<DocumentView> documents) {
}
