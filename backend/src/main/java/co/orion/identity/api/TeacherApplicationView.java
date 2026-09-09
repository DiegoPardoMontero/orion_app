package co.orion.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La postulación vista por el propio aspirante: estado, feedback del admin, lista de requisitos que
 * aún faltan para poder enviarla, sus documentos y <strong>lo que ya respondió</strong>.
 *
 * <p>{@code answers} existe porque sin él el aspirante no tenía forma de recuperar lo suyo. Su
 * perfil se guarda en {@code professor_profiles}, pero {@code /me/profile} exige rol PROFESSOR y un
 * aspirante todavía no lo es, así que el wizard se dibujaba en blanco cada vez que volvía — y al
 * avanzar de paso mandaba ese blanco al servidor. La postulación es su objeto: tiene que
 * devolverle lo que puso en ella.
 */
public record TeacherApplicationView(
        UUID id,
        String status,
        Instant submittedAt,
        String decisionNote,
        boolean agreementAccepted,
        List<String> missing,
        List<DocumentView> documents,
        ProfileResponse answers) {
}
