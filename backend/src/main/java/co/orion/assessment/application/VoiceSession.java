package co.orion.assessment.application;

import java.time.Instant;

/**
 * Con qué se conecta el navegador, y nada más.
 *
 * @param sessionRef   identificador de la sesión en el proveedor, para poder cerrarla y auditarla
 * @param clientSecret credencial <strong>efímera</strong>: vale minutos y solo para esta sesión.
 *                     Nunca viaja la llave de la cuenta al navegador
 * @param expiresAt    cuándo deja de valer la credencial
 * @param model        qué modelo quedó atado a la sesión, para que el registro de consumo lo diga
 */
public record VoiceSession(String sessionRef,
                           String clientSecret,
                           Instant expiresAt,
                           String model) {
}
