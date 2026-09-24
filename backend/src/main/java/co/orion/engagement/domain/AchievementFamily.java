package co.orion.engagement.domain;

/** Las constelaciones del cielo. El color sale de aquí: durazno o lavanda. */
public enum AchievementFamily {
    PRIMEROS,
    CONSTANCIA,
    VOLUMEN,
    AMPLITUD,
    COMPROMISO,
    /** La práctica entre clases (24/09/2026): es de Meissa, así que va en lavanda. */
    PRACTICA;

    /**
     * El color de la familia, según §2a del diseño: durazno para los primeros pasos, la constancia
     * y el volumen; lavanda para amplitud y compromiso.
     */
    public boolean esLavanda() {
        return this == AMPLITUD || this == COMPROMISO || this == PRACTICA;
    }
}
