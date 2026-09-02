package co.orion.messaging.domain;

/**
 * Por qué un mensaje quedó marcado por la política de contacto. {@code CONTACT_INFO} para
 * teléfonos y correos; {@code OFF_PLATFORM} para menciones de canales externos (WhatsApp,
 * Telegram…); {@code OTHER} reservado para marcados manuales del admin.
 */
public enum FlaggedReason {
    CONTACT_INFO,
    OFF_PLATFORM,
    OTHER
}
