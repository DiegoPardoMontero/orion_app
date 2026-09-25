package co.orion.messaging.domain;

/**
 * Los mensajes que Rigel manda a nombre de Orión (V66). Cada uno sale una sola vez por persona,
 * salvo {@link #COME_BACK}, que puede repetirse —a lo sumo uno al mes—.
 */
public enum RigelKind {
    /** Al estudiante, la primera vez que entra: cómo funciona Orión. */
    WELCOME_STUDENT,
    /** Al profesor, ya aprobado: qué hacer para recibir estudiantes. */
    WELCOME_PROFESSOR,
    FIRST_BOOKING_STUDENT,
    FIRST_BOOKING_PROFESSOR,
    FIRST_CLASS_STUDENT,
    FIRST_CLASS_PROFESSOR,
    /** Al estudiante que lleva semanas sin clase: una invitación a volver, sin insistir. */
    COME_BACK
}
