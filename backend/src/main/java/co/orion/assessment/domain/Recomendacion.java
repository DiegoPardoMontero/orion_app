package co.orion.assessment.domain;

import java.util.UUID;

/**
 * Un profesor recomendado, con su posición y el porqué ya redactado.
 *
 * <p>Se guarda el texto y no solo el código porque la plantilla puede cambiar: quien abra su
 * diagnóstico dentro de un año tiene que leer lo que se le dijo entonces, no lo que diríamos hoy.
 */
public record Recomendacion(UUID professorId, int position, ReasonCode reasonCode, String reasonText) {
}
