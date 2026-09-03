package co.orion.messaging.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Con quién se quiere hablar. Es "la contraparte" y no "el profesor" porque los dos lados pueden
 * abrir el hilo: el estudiante nombra a un profesor, el profesor a un estudiante suyo. Quién puede
 * escribirle a quién lo decide el servicio según el rol de quien pide, no este DTO.
 */
public record CreateConversationRequest(@NotNull UUID counterpartId) {
}
