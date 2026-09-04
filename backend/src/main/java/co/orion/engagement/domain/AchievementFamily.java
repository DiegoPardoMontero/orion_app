package co.orion.engagement.domain;

/** Las cinco constelaciones del cielo. El color sale de aquí: durazno o lavanda. */
public enum AchievementFamily {
    PRIMEROS,
    CONSTANCIA,
    VOLUMEN,
    AMPLITUD,
    COMPROMISO;

    /**
     * El color de la familia, según §2a del diseño: durazno para los primeros pasos, la constancia
     * y el volumen; lavanda para amplitud y compromiso.
     */
    public boolean esLavanda() {
        return this == AMPLITUD || this == COMPROMISO;
    }
}
