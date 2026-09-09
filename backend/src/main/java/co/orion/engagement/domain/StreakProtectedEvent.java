package co.orion.engagement.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Se gastó una protección: hubo una semana sin clase y la racha siguió en pie.
 *
 * <p>Existe para poder decirlo. La protección se aplica sola, hacia atrás y en silencio, así que
 * el estudiante veía una estrella encendida en una semana en la que no tuvo clase y una palabra
 * —«protegida»— que nadie le había explicado. Un mecanismo pensado para animar que solo se
 * descubre por sorpresa no anima: desconcierta.
 */
public record StreakProtectedEvent(UUID studentId, LocalDate semana) {
}
