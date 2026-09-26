package co.orion.catalog.domain;

import java.net.URI;
import java.net.URISyntaxException;
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
                    + "reservadas conservan la comisión que tenían. Los profes fundadores pagan la suya "
                    + "mientras dure su beneficio.",
            0, 5000, true),
    FOUNDER_COMMISSION_RATE_BPS("founder_commission_rate_bps", Grupo.DINERO, Tipo.ENTERO,
            "Comisión de profe fundador",
            "En puntos básicos: 1500 son 15 %. Se copia al perfil del profe cuando se le otorga el "
                    + "beneficio, así que cambiarla solo afecta a los fundadores nuevos.",
            0, 5000, true),
    FOUNDER_PERIOD_MONTHS("founder_period_months", Grupo.DINERO, Tipo.ENTERO,
            "Meses de beneficio del profe fundador",
            "Cuenta desde su primera clase pagada. Como la comisión, se copia al otorgarlo: cambiarlo "
                    + "no toca a quien ya es fundador.",
            1, 24, true),
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
            "Minutos de conversación antes del corte duro. Subirlo alarga la conversación y la "
                    + "factura a la vez: se paga por minuto hablado.", 2, 15, false),
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
    ASSESSMENT_LEAD_RETENTION_DAYS("assessment_lead_retention_days", Grupo.PLAZOS, Tipo.ENTERO,
            "Días que se guarda un diagnóstico sin cuenta",
            "Si la persona no crea su cuenta en este plazo, se borra entero: su nombre, lo que dijo "
                    + "y su resultado.", 1, 365, true),

    // El acta de clase (Bloque 10). Presupuesto propio, aparte del diagnóstico: uno por función.
    AI_LESSON_NOTES_ENABLED("ai_lesson_notes_enabled", Grupo.POLITICAS, Tipo.BOOLEANO,
            "Borrador del acta con IA",
            "Apagado, el profesor escribe el acta a mano en los mismos cuatro campos: la función no "
                    + "desaparece, solo deja de proponer el borrador.",
            true),
    AI_DAILY_BUDGET_COP("ai_daily_budget_cop", Grupo.DINERO, Tipo.ENTERO,
            "Presupuesto diario de las actas",
            "Pesos al día en IA para los borradores de acta. Al llegar al tope el profesor escribe a "
                    + "mano hasta mañana; al 80 % sale un aviso por correo.", 0, 5_000_000, true),
    AI_NOTE_TIMEOUT_SECONDS("ai_note_timeout_seconds", Grupo.PLAZOS, Tipo.ENTERO,
            "Espera máxima del borrador del acta",
            "Segundos que se espera a la IA antes de pasar al camino a mano.", 5, 60, false),
    LESSON_NOTE_EDIT_WINDOW_HOURS("lesson_note_edit_window_hours", Grupo.PLAZOS, Tipo.ENTERO,
            "Horas para corregir un acta publicada",
            "Pasado el plazo el acta queda como está. Si se corrige antes, el estudiante ve "
                    + "«Actualizada el…».", 1, 336, false),
    LESSON_NOTE_NUDGE_MINUTES("lesson_note_nudge_minutes", Grupo.PLAZOS, Tipo.ENTERO,
            "Recordatorio del acta",
            "Minutos después de cerrarse la clase en que se le recuerda al profesor, una sola vez, "
                    + "que escriba el acta.", 0, 1440, false),

    // La práctica entre clases (Bloque 10, Parte B).
    PRACTICE_ENABLED("practice_enabled", Grupo.POLITICAS, Tipo.BOOLEANO,
            "Práctica entre clases",
            "Apagada, las actas se siguen publicando pero no generan ejercicios, y la invitación a "
                    + "practicar desaparece sin dejar rastro.",
            true),
    PRACTICE_SET_TTL_DAYS("practice_set_ttl_days", Grupo.PLAZOS, Tipo.ENTERO,
            "Días que dura una práctica",
            "Pasado el plazo, el set deja de ofrecerse: el viejo no compite con el de la clase siguiente.",
            1, 30, false),
    PRACTICE_ITEMS_PER_SET("practice_items_per_set", Grupo.POLITICAS, Tipo.ENTERO,
            "Ejercicios por práctica",
            "Cuántos ejercicios trae cada set. Más ejercicios, más minutos para el estudiante.",
            2, 8, false),
    PRACTICE_MAX_ATTEMPTS("practice_max_attempts", Grupo.POLITICAS, Tipo.ENTERO,
            "Intentos por ejercicio",
            "Después se muestra la respuesta con su explicación y se sigue.", 1, 5, false),
    PRACTICE_DAILY_BUDGET_COP("practice_daily_budget_cop", Grupo.DINERO, Tipo.ENTERO,
            "Presupuesto diario de la práctica",
            "Pesos al día en IA para generar ejercicios, aparte del acta y del diagnóstico. Al llegar "
                    + "al tope, los sets nuevos esperan a mañana; al 80 % sale un aviso.",
            0, 5_000_000, true),

    // Los pesos del Confidence Score. Suman 100 por convención, no por obligación: el cálculo
    // pondera sobre el total que haya. Cambiar cualquiera cambia la versión del puntaje, así que
    // los diagnósticos de antes siguen siendo comparables entre ellos y no con los de después.
    SCORE_WEIGHT_ARRANQUE("score_weight_arranque", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso · arranque",
            "Cuánto pesa lo que tarda en empezar a responder. Es el marcador más directo de la "
                    + "confianza al hablar.", 0, 100, true),
    SCORE_WEIGHT_CONTINUIDAD("score_weight_continuidad", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso · continuidad",
            "Cuánto pesa empezar frases y soltarlas a mitad.", 0, 100, true),
    SCORE_WEIGHT_EXTENSION("score_weight_extension", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso · extensión",
            "Cuánto pesa lo largo que responde.", 0, 100, true),
    SCORE_WEIGHT_AUTONOMIA("score_weight_autonomia", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso · autonomía",
            "Cuánto pesa devolverse al idioma propio cuando el terreno se pone difícil.",
            0, 100, true),
    SCORE_WEIGHT_SOLTURA("score_weight_soltura", Grupo.REPUTACION, Tipo.ENTERO,
            "Peso · soltura",
            "Cuánto pesan las muletillas y las autocorrecciones por cada cien palabras.",
            0, 100, true),

    APPLICATION_REVIEW_BUSINESS_DAYS("application_review_business_days", Grupo.PLAZOS, Tipo.ENTERO,
            "Plazo para revisar una postulación",
            "Días hábiles que se le prometen al aspirante cuando envía su postulación. Se le "
                    + "muestra en pantalla: subirlo es incómodo, pero incumplirlo es peor.",
            1, 30, false),
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
            false),

    // ------------------------------------------------------------------------- mandato
    MANDATARY_NAME("mandatary_name", Grupo.DINERO, Tipo.TEXTO,
            "Nombre del mandatario",
            "Quien recibe el dinero de las clases por cuenta de cada profe y se lo entrega. Sale en el "
                    + "comprobante de cada liquidación y en el certificado anual. Vacío: el responsable "
                    + "de los datos legales.",
            false),
    MANDATARY_DOCUMENT("mandatary_document", Grupo.DINERO, Tipo.TEXTO,
            "Documento del mandatario",
            "Cédula o NIT, como aparece en el RUT. Sale en el comprobante y en el certificado anual. "
                    + "Vacío: el documento de los datos legales.",
            false),

    UVT_COP("uvt_cop", Grupo.DINERO, Tipo.ENTERO,
            "Valor de la UVT",
            "En pesos, el del año en curso (la DIAN lo publica cada diciembre). El panel expresa en UVT "
                    + "el recaudo del año, junto a la referencia de 3.500 UVT.",
            1, 10_000_000, false),

    // ------------------------------------------------------------------------- contenido
    PROFESSOR_WELCOME_VIDEO_URL("professor_welcome_video_url", Grupo.CONTENIDO, Tipo.ENLACE,
            "Video de bienvenida para profesores",
            "El video de Sofía que cada profesor aprobado ve una vez, al entrar. Sirve un enlace de "
                    + "YouTube (puede ser oculto), Vimeo, Google Drive o un .mp4. Vacío, no aparece nada.",
            false);

    public enum Grupo { DINERO, PLAZOS, REPUTACION, POLITICAS, CONTENIDO }

    /**
     * {@code ENLACE} y {@code TEXTO} admiten quedar vacíos: un enlace vacío es «no hay», y un texto
     * vacío es «usar el de siempre» (el mandatario, por ejemplo, cae a los datos legales).
     */
    public enum Tipo { ENTERO, BOOLEANO, OPCION, ENLACE, TEXTO }

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
        if (value.isEmpty() && (tipo == Tipo.ENLACE || tipo == Tipo.TEXTO)) {
            return "";
        }
        if (value.isEmpty()) {
            throw new UnprocessableException("«" + etiqueta + "» no puede quedar vacío.");
        }
        return switch (tipo) {
            case ENTERO -> validarEntero(value);
            case BOOLEANO -> validarBooleano(value);
            case OPCION -> validarOpcion(value);
            case ENLACE -> validarEnlace(value);
            case TEXTO -> validarTexto(value);
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

    private String validarTexto(String value) {
        if (value.length() > 150) {
            throw new UnprocessableException("«" + etiqueta + "» admite hasta 150 caracteres.");
        }
        return value;
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

    /**
     * Solo https y con dominio: el enlace termina dentro de un reproductor en la página del
     * profesor, y un {@code javascript:} o un {@code http:} ahí serían, respectivamente, un agujero
     * y un aviso de contenido mixto.
     */
    private String validarEnlace(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException ex) {
            throw new UnprocessableException("«" + etiqueta + "» no es un enlace válido.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || value.length() > 500) {
            throw new UnprocessableException("«" + etiqueta + "» tiene que ser un enlace https://.");
        }
        return value;
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
