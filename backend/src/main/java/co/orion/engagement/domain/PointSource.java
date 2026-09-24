package co.orion.engagement.domain;

/**
 * Las maneras de hacer puntos y cuánto da cada una (24/09/2026: «un score que no significa nada más
 * que hacer puntos»). Es la columna {@code source_type} del libro; el nombre de la constante es lo
 * que se guarda.
 *
 * <p>Las de {@code unaVez} se conceden una sola vez por persona: su {@code source_id} es el propio
 * estudiante, y el índice único del libro hace el resto. Los logros dan lo que diga el catálogo.
 */
public enum PointSource {

    LESSON(25, false),
    /** Entrar al aula a tiempo: a más tardar cinco minutos después de la hora. Lo cuenta JaaS. */
    PUNCTUAL(5, false),
    REVIEW(20, false),
    PRACTICE(15, false),
    PRACTICE_PERFECT(5, false),
    /** Escribirle a un profe por primera vez: una por conversación, no por mensaje. */
    MESSAGE(5, false),
    PROFILE_PUBLIC(10, true),
    DIAGNOSTIC(20, true),
    TOUR(10, true),
    ACHIEVEMENT(0, false);

    private final int points;
    private final boolean unaVez;

    PointSource(int points, boolean unaVez) {
        this.points = points;
        this.unaVez = unaVez;
    }

    public int points() {
        return points;
    }

    public boolean unaVez() {
        return unaVez;
    }
}
