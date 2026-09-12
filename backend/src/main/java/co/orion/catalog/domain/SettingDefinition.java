package co.orion.catalog.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import co.orion.shared.error.UnprocessableException;

/**
 * Qué es cada ajuste y qué valores admite.
 *
 * <p>Hasta ahora {@code PUT /admin/settings/{key}} aceptaba cualquier texto: un cero de más en
 * {@code commission_rate_bps} cambiaba lo que cobra cada reserva desde ese instante, y un
 * {@code sanctions_mode} mal escrito habría roto la evaluación de sanciones en silencio. La
 * validación vive aquí y no en el formulario porque el formulario no es la única puerta.
 *
 * <p>{@code sensitive} marca lo que exige confirmación escrita en la interfaz. No es una categoría
 * de seguridad —quien tiene el rol puede cambiarlo igual— sino un freno contra el descuido, que es
 * de lo que hay que protegerse aquí.
 */
public enum SettingDefinition {

    // ---------------------------------------------------------------------------- dinero
    COMMISSION_RATE_BPS("commission_rate_bps", Grupo.DINERO, Tipo.ENTERO,
            "Comisión de Orión",
            "En puntos básicos: 2000 son 20 %. Se congela en cada reserva, así que las clases ya "
                    + "reservadas conservan la comisión que tenían.",
            0, 5000, true),
    PAYMENT_HOLD_MINUTES("payment_hold_minutes", Grupo.DINERO, Tipo.ENTERO,
            "Minutos para pagar",
            "Cuánto tiempo se aparta el cupo mientras el estudiante paga.", 5, 120, false),

    // ---------------------------------------------------------------------------- plazos
    STUDENT_CANCEL_HOURS("student_cancel_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Horas para que el estudiante cancele",
            "Con menos de estas horas por delante ya no puede cancelar, solo proponer otro horario.",
            0, 168, true),
    PROFESSOR_CANCEL_HOURS("professor_cancel_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Horas para que el profesor cancele",
            "El mismo plazo del otro lado. Cambiar uno solo rompe la simetría que dicen los Términos.",
            0, 168, true),
    // --------------------------------------------------------------- diagnóstico de confianza
    ASSESSMENT_ENABLED("assessment_enabled", Grupo.POLITICAS, Tipo.BOOLEANO,
            "Diagnóstico de confianza activo",
            "Apagarlo retira el bloque de la portada sin dejar rastro: ni mensaje de error ni "
                    + "botón deshabilitado. Quien llegue verá el buscador de siempre.",
            true),
    ASSESSMENT_MAX_MINUTES("assessment_max_minutes", Grupo.PLAZOS, Tipo.ENTERO,
            "Duración máxima del diagnóstico",
            "Minutos de conversación antes del corte duro.", 3, 15, false),
    ASSESSMENT_MIN_MINUTES("assessment_min_minutes", Grupo.PLAZOS, Tipo.ENTERO,
            "Duración mínima para puntuar",
            "Por debajo de esto no se calcula puntaje: un número sacado de noventa segundos es "
                    + "peor que ningún número.", 1, 10, false),
    ASSESSMENT_COOLDOWN_DAYS("assessment_cooldown_days", Grupo.PLAZOS, Tipo.ENTERO,
            "Días entre un diagnóstico y el siguiente",
            "Cuánto hay que esperar para repetirlo.", 1, 365, false),
    ASSESSMENT_DAILY_BUDGET_COP("assessment_daily_budget_cop", Grupo.DINERO, Tipo.ENTERO,
            "Presupuesto diario del diagnóstico",
            "Pesos al día en IA. Al llegar al tope la función se apaga sola hasta mañana; al 80 % "
                    + "sale un aviso por correo.", 0, 5_000_000, true),
    ASSESSMENT_RETENTION_DAYS("assessment_transcript_retention_days", Grupo.PLAZOS, Tipo.ENTERO,
            "Días que se guarda la transcripción",
            "Pasado el plazo se borra lo que la persona dijo y se conservan puntaje, señales y "
                    + "resumen. El audio no se guarda nunca.", 30, 1825, true),

    BOOKING_MIN_LEAD_HOURS("booking_min_lead_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Antelación mínima para reservar",
            "Con menos de estas horas por delante, el cupo ya no se ofrece ni se puede reservar. "
                    + "Subirlo por encima de las horas de cancelación deja al estudiante sin margen "
                    + "para arrepentirse.",
            0, 72, true),
    NO_SHOW_REPORT_MINUTES("no_show_report_minutes", Grupo.PLAZOS, Tipo.ENTERO,
            "Espera antes de reportar una ausencia",
            "Minutos desde la hora de inicio antes de poder reportar que el profesor no llegó.",
            0, 120, false),
    DISPUTE_REPORT_WINDOW_HOURS("dispute_report_window_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Horas para reclamar",
            "Plazo máximo para abrir un reclamo después de que la clase termina.", 1, 168, false),
    AUTO_COMPLETE_HOURS("auto_complete_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Horas hasta el cierre automático",
            "Tras las cuales una clase sin reclamo se cierra sola y su pago se libera.",
            1, 168, true),
    RESCHEDULE_MIN_HOURS("reschedule_min_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Anticipación mínima al reprogramar",
            "Un horario propuesto tiene que estar al menos a estas horas de distancia.", 0, 72, false),

    // ------------------------------------------------------------------------ reputación
    METRICS_WINDOW_DAYS("metrics_window_days", Grupo.REPUTACION, Tipo.ENTERO,
            "Ventana de las métricas",
            "Días que mira el desempeño de un profesor. Todo lo anterior deja de contar.",
            7, 365, false),
    RANKING_COLD_START_LESSONS("ranking_cold_start_lessons", Grupo.REPUTACION, Tipo.ENTERO,
            "Clases para salir del arranque en frío",
            "Por debajo de esto, un profesor recibe una posición neutra en vez de quedar último.",
            0, 50, false),
    SANCTIONS_MODE("sanctions_mode", Grupo.REPUTACION, Tipo.OPCION,
            "Modo de las sanciones",
            "OBSERVE las propone y espera tu confirmación; ENFORCE las aplica solas.",
            Set.of("OBSERVE", "ENFORCE"), true),
    SANCTION_PENALTY_POINTS("sanction_penalty_points", Grupo.REPUTACION, Tipo.ENTERO,
            "Puntos de penalización por sanción",
            "Cuánto baja en el ranking un profesor con una sanción activa.", 0, 1000, false),
    RANKING_WEIGHT_RATING("ranking_weight_rating", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso: calificación", "Cuánto pesa la calificación en el orden del buscador.",
            0, 1000, false),
    RANKING_WEIGHT_ATTENDANCE("ranking_weight_attendance", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso: cumplimiento", "Cuánto pesa presentarse a las clases.", 0, 1000, false),
    RANKING_WEIGHT_LESSONS("ranking_weight_lessons", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso: clases dictadas", "Cuánto pesa el volumen de clases.", 0, 1000, false),
    RANKING_WEIGHT_COMPLETENESS("ranking_weight_completeness", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso: perfil completo", "Cuánto pesa tener la ficha llena.", 0, 1000, false),
    RANKING_WEIGHT_RESPONSE("ranking_weight_response", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso: tasa de respuesta",
            "Cuánto pesaría responder los mensajes. Hoy no se mide, así que no penaliza a nadie.",
            0, 1000, false),
    RANKING_WEIGHT_RETENTION("ranking_weight_retention", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso: estudiantes que vuelven", "Cuánto pesa que los estudiantes repitan.",
            0, 1000, false),

    // ------------------------------------------------------------------------- políticas
    CONTACT_POLICY_MODE("contact_policy_mode", Grupo.POLITICAS, Tipo.OPCION,
            "Política de contacto en mensajes",
            "MASK oculta teléfonos y correos dentro de los mensajes; OFF los deja pasar.",
            Set.of("MASK", "OFF"), true),
    REQUIRE_PHONE_VERIFICATION("require_phone_verification", Grupo.POLITICAS, Tipo.BOOLEANO,
            "Exigir verificación de teléfono",
            "Hoy no hay flujo de verificación de teléfono: dejarlo en verdadero no verifica nada.",
            false),
    GAMIFICATION_COUNT_FREE_LESSONS("gamification_count_free_lessons", Grupo.POLITICAS,
            Tipo.BOOLEANO, "Las clases gratuitas suman puntos",
            "Por defecto no: las clases de prueba en producción no deben ensuciar el perfil de nadie.",
            false);

    public enum Grupo { DINERO, PLAZOS, REPUTACION, POLITICAS }

    public enum Tipo { ENTERO, BOOLEANO, OPCION }

    private final String key;
    private final Grupo grupo;
    private final Tipo tipo;
    private final String etiqueta;
    private final String explicacion;
    private final Integer min;
    private final Integer max;
    private final Set<String> opciones;
    private final boolean sensitive;

    SettingDefinition(String key, Grupo grupo, Tipo tipo, String etiqueta, String explicacion,
                      int min, int max, boolean sensitive) {
        this(key, grupo, tipo, etiqueta, explicacion, min, max, Set.of(), sensitive);
    }

    SettingDefinition(String key, Grupo grupo, Tipo tipo, String etiqueta, String explicacion,
                      Set<String> opciones, boolean sensitive) {
        this(key, grupo, tipo, etiqueta, explicacion, null, null, opciones, sensitive);
    }

    SettingDefinition(String key, Grupo grupo, Tipo tipo, String etiqueta, String explicacion,
                      boolean sensitive) {
        this(key, grupo, tipo, etiqueta, explicacion, null, null, Set.of(), sensitive);
    }

    SettingDefinition(String key, Grupo grupo, Tipo tipo, String etiqueta, String explicacion,
                      Integer min, Integer max, Set<String> opciones, boolean sensitive) {
        this.key = key;
        this.grupo = grupo;
        this.tipo = tipo;
        this.etiqueta = etiqueta;
        this.explicacion = explicacion;
        this.min = min;
        this.max = max;
        this.opciones = opciones;
        this.sensitive = sensitive;
    }

    public static Optional<SettingDefinition> forKey(String key) {
        return Arrays.stream(values()).filter(d -> d.key.equals(key)).findFirst();
    }

    /**
     * Valida el valor y lo devuelve normalizado. Lanza 422 con lo que sí se admite: un ajuste
     * rechazado sin decir el rango deja a quien lo intenta adivinando.
     */
    public String validate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new UnprocessableException("«" + etiqueta + "» no puede quedar vacío.");
        }
        return switch (tipo) {
            case ENTERO -> validarEntero(value);
            case BOOLEANO -> validarBooleano(value);
            case OPCION -> validarOpcion(value);
        };
    }

    private String validarEntero(String value) {
        int numero;
        try {
            numero = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new UnprocessableException("«" + etiqueta + "» tiene que ser un número entero.");
        }
        if (min != null && numero < min || max != null && numero > max) {
            throw new UnprocessableException(
                    "«" + etiqueta + "» debe estar entre " + min + " y " + max + ".");
        }
        return String.valueOf(numero);
    }

    private String validarBooleano(String value) {
        String lower = value.toLowerCase();
        if (!lower.equals("true") && !lower.equals("false")) {
            throw new UnprocessableException("«" + etiqueta + "» solo admite verdadero o falso.");
        }
        return lower;
    }

    private String validarOpcion(String value) {
        String upper = value.toUpperCase();
        if (!opciones.contains(upper)) {
            throw new UnprocessableException("«" + etiqueta + "» solo admite: "
                    + String.join(", ", opciones.stream().sorted().toList()) + ".");
        }
        return upper;
    }

    public String getKey() {
        return key;
    }

    public Grupo getGrupo() {
        return grupo;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getExplicacion() {
        return explicacion;
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }

    public List<String> getOpciones() {
        return opciones.stream().sorted().toList();
    }

    public boolean isSensitive() {
        return sensitive;
    }
}
