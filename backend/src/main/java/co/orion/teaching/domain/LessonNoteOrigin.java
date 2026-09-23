package co.orion.teaching.domain;

public enum LessonNoteOrigin {
    /** La IA propuso el borrador y el profesor lo revisó. */
    AI_DRAFT,
    /** La IA no estaba disponible o falló, y el profesor la escribió a mano en los mismos campos. */
    MANUAL
}
