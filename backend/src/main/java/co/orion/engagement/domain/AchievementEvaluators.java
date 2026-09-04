package co.orion.engagement.domain;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Los ocho evaluadores. Cada uno recibe la foto del estudiante y los parámetros del logro, y
 * devuelve el progreso — un número que se compara con {@code target}.
 *
 * <p>El catálogo es data-driven pero <strong>no</strong> con un motor genérico de reglas en JSONB:
 * eso es un intérprete a medio hacer que nadie sabe depurar cuando falla en producción. Ocho
 * funciones tipadas cubren los veinte logros, y un logro nuevo del mismo tipo sigue siendo un
 * INSERT sin tocar código.
 *
 * <p>Son funciones puras: sin Spring, sin repositorios y sin reloj del sistema. Es lo que permite
 * probarlos con datos en memoria y lo que hace que el recálculo y el procesamiento incremental
 * lleguen al mismo estado.
 */
public final class AchievementEvaluators {

    private AchievementEvaluators() {
    }

    /** Cuántos parámetros lee cada evaluador viene documentado en su entrada del mapa. */
    private static final Map<CriteriaType, BiFunction<AchievementInput, Map<String, String>, Integer>>
            EVALUADORES = Map.of(

            CriteriaType.LESSON_COUNT,
            (in, params) -> in.clasesTomadas().size(),

            CriteriaType.STREAK_WEEKS,
            (in, params) -> StreakCalculator
                    .calcular(in.clasesTomadas(), in.mesesYaProtegidos(), in.ahora())
                    .actual(),

            CriteriaType.DISTINCT_PROFESSORS,
            (in, params) -> in.profesores().size(),

            // Solo los idiomas conocidos: una reserva anterior a la V20 sin idioma no puede
            // contarse como "otro idioma" porque no sabemos cuál era.
            CriteriaType.DISTINCT_LANGUAGES,
            (in, params) -> in.idiomas().size(),

            CriteriaType.MODALITY_TAKEN,
            (in, params) -> "IN_PERSON".equals(params.get("modality"))
                    ? (int) Math.min(Integer.MAX_VALUE, in.presenciales())
                    : in.clasesTomadas().size() - (int) in.presenciales(),

            CriteriaType.EVENT_ONCE,
            (in, params) -> in.eventosOcurridos().contains(params.get("event")) ? 1 : 0,

            CriteriaType.PROFILE_COMPLETE,
            (in, params) -> in.camposDePerfil(),

            CriteriaType.NO_CANCELLATIONS_DAYS,
            (in, params) -> (int) Math.min(Integer.MAX_VALUE, in.diasSinCancelar()));

    /**
     * El progreso de un logro. Un tipo sin evaluador es un error de programación —el catálogo y el
     * enum se escriben juntos—, así que falla ruidosamente en vez de devolver cero en silencio.
     */
    public static int progresoDe(CriteriaType tipo, AchievementInput input, Map<String, String> params) {
        var evaluador = EVALUADORES.get(tipo);
        if (evaluador == null) {
            throw new IllegalStateException("Sin evaluador para el criterio " + tipo);
        }
        return Math.max(0, evaluador.apply(input, params));
    }

    /** Que no falte ninguno: se comprueba al arrancar y en un test. */
    public static boolean cubreTodosLosCriterios() {
        return EVALUADORES.keySet().containsAll(java.util.Set.of(CriteriaType.values()));
    }
}
