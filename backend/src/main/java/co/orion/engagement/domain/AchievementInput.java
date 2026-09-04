package co.orion.engagement.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import co.orion.scheduling.domain.LearningProgress.Tomada;

/**
 * Todo lo que hace falta para evaluar los 20 logros de un estudiante, resuelto de una vez.
 *
 * <p>Existe para que los evaluadores sean <strong>funciones puras</strong>: reciben esto y
 * devuelven un número, sin tocar la base. Es lo que permite probar los veinte logros con datos en
 * memoria y, sobre todo, lo que hace que el recálculo desde cero y el procesamiento incremental
 * lleguen exactamente al mismo estado — porque ambos evalúan sobre la misma foto.
 *
 * @param clasesTomadas las clases que cuentan (COMPLETED, sin no-show y sin gratuitas si el ajuste
 *                      lo dice); ya vienen filtradas
 * @param presenciales cuántas de esas fueron presenciales
 * @param idiomas los idiomas distintos y no nulos de esas clases
 * @param profesores los profesores distintos de esas clases
 * @param eventosOcurridos qué hechos puntuales han ocurrido: `booking_created`, `message_sent`,
 *                         `review_written`, `goal_declared`
 * @param camposDePerfil cuántos de los tres campos de «Perfil listo» están puestos (0–3)
 * @param diasSinCancelar días corridos desde la última cancelación del estudiante
 * @param mesesYaProtegidos meses en los que ya gastó su protección de racha
 * @param ahora el instante de referencia
 */
public record AchievementInput(List<Tomada> clasesTomadas,
                               long presenciales,
                               Set<String> idiomas,
                               Set<UUID> profesores,
                               Set<String> eventosOcurridos,
                               int camposDePerfil,
                               long diasSinCancelar,
                               Set<java.time.LocalDate> mesesYaProtegidos,
                               Instant ahora) {
}
