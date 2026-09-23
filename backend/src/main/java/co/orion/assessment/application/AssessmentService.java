package co.orion.assessment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.assessment.domain.AssessmentMode;
import co.orion.assessment.domain.AssessmentRecommendation;
import co.orion.assessment.domain.AssessmentStatus;
import co.orion.assessment.domain.AssessmentTurn;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.assessment.domain.ConfidenceScoreCalculator;
import co.orion.assessment.domain.IdiomaDeLaFrase;
import co.orion.assessment.domain.PesosDelPuntaje;
import co.orion.assessment.domain.Puntaje;
import co.orion.assessment.domain.Recomendacion;
import co.orion.assessment.domain.ResumenDePlantilla;
import co.orion.assessment.domain.SignalExtractor;
import co.orion.assessment.domain.TurnoDelUsuario;
import co.orion.assessment.domain.VoiceConsent;
import co.orion.assessment.persistence.AssessmentRecommendationRepository;
import co.orion.assessment.persistence.AssessmentTurnRepository;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.assessment.persistence.VoiceConsentRepository;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.catalog.domain.TeachingGoal;
import co.orion.catalog.persistence.TeachingGoalRepository;
import co.orion.identity.domain.User;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.time.BusinessZone;

/**
 * El diagnóstico de confianza, de principio a fin.
 *
 * <p><strong>Quién lo hace: una cuenta o un lead</strong> ({@link Evaluado}). Desde el 22/09/2026
 * no hace falta cuenta para hablar con Meissa: el lead trae su nombre y sus dos declaraciones, y
 * su autorización de voz va en él. A una cuenta se le pide la suya, con su 422 y su motivo exacto.
 * Ya no se exige el correo verificado: pedírselo a quien tiene cuenta y no a quien no la tiene no
 * protegería nada. Y el enfriamiento responde 409 con la fecha en que puede repetir, porque «no
 * puedes» sin «cuándo sí» es una puerta cerrada sin cartel.
 *
 * <p><strong>Las señales las deduce el servidor.</strong> El cliente manda lo que solo él puede
 * medir —cuánto tardó en arrancar y cuánto habló— y el texto. Todo lo demás sale de
 * {@link SignalExtractor} aquí dentro. Un puntaje que dependiera de números enviados por el
 * navegador no sería reproducible, y cualquiera podría regalarse un cien.
 *
 * <p><strong>Sin turnos suficientes no hay número.</strong> Se cierra como ABANDONED. Un puntaje
 * sacado de dos frases parece un dato y no lo es, y este número lleva una marca registrada encima.
 */
@Service
public class AssessmentService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** La versión del texto de consentimiento que se está pidiendo hoy. */
    public static final String VERSION_CONSENTIMIENTO = "1.0";

    private final ConfidenceAssessmentRepository assessments;
    private final AssessmentTurnRepository turns;
    private final AssessmentRecommendationRepository recommendations;
    private final VoiceConsentRepository consents;
    private final RecommendationService recommender;
    private final AssessmentBudgetService budget;
    private final VoiceConversationProvider voice;
    private final ScenarioPrompts prompts;
    private final ConversationSummarizer resumidor;
    private final TeachingGoalRepository objetivosDelCatalogo;
    private final PlatformSettingsService settings;
    private final AiUsageRecorder usage;
    private final TraductorDeFrases traductor;
    private final Clock clock;

    public AssessmentService(ConfidenceAssessmentRepository assessments,
                             AssessmentTurnRepository turns,
                             AssessmentRecommendationRepository recommendations,
                             VoiceConsentRepository consents,
                             RecommendationService recommender,
                             AssessmentBudgetService budget,
                             VoiceConversationProvider voice,
                             ScenarioPrompts prompts,
                             ConversationSummarizer resumidor,
                             TeachingGoalRepository objetivosDelCatalogo,
                             PlatformSettingsService settings,
                             AiUsageRecorder usage,
                             TraductorDeFrases traductor,
                             Clock clock) {
        this.assessments = assessments;
        this.turns = turns;
        this.recommendations = recommendations;
        this.consents = consents;
        this.recommender = recommender;
        this.budget = budget;
        this.voice = voice;
        this.prompts = prompts;
        this.resumidor = resumidor;
        this.objetivosDelCatalogo = objetivosDelCatalogo;
        this.settings = settings;
        this.usage = usage;
        this.traductor = traductor;
        this.clock = clock;
    }

    /* ---------------- Consentimiento ---------------- */

    @Transactional
    public VoiceConsent acceptConsent(User quien, String ip, String userAgent) {
        return consents.save(new VoiceConsent(
                quien.getId(), VERSION_CONSENTIMIENTO, clock.instant(), ip, userAgent));
    }

    /** Revocar no borra aquí: marca. El borrado de sus turnos lo hace el job, y se le confirma. */
    @Transactional
    public void revokeConsent(User quien) {
        VoiceConsent vigente = consents.findFirstByUserIdOrderByAcceptedAtDesc(quien.getId())
                .filter(VoiceConsent::isLive)
                .orElseThrow(() -> new UnprocessableException(
                        "No tienes un consentimiento de voz activo que revocar."));
        vigente.revoke(clock.instant());
        consents.save(vigente);
    }

    @Transactional(readOnly = true)
    public boolean hasLiveConsent(UUID userId) {
        return consents.findFirstByUserIdOrderByAcceptedAtDesc(userId)
                .filter(VoiceConsent::isLive).isPresent();
    }

    /* ---------------- Empezar ---------------- */

    @Transactional
    public Iniciada start(Evaluado quien, String languageCode) {
        // El lead trae su autorización de voz desde que se creó; la cuenta la da aparte.
        if (!quien.esLead() && !hasLiveConsent(quien.userId())) {
            throw new UnprocessableException(
                    "Necesitamos tu autorización para procesar tu voz antes de empezar.");
        }
        if (!budget.disponible()) {
            // 422 y no 400: la petición no tiene nada de malo. Somos nosotros los que hoy no
            // podemos atenderla, y la diferencia importa para quien lee el error y para el log.
            throw new UnprocessableException(
                    "El diagnóstico no está disponible ahora mismo. Vuelve a intentarlo más tarde.");
        }

        // Una viva por persona e idioma: si quedó abierta, se reutiliza en vez de abrir otra. Los
        // índices únicos lo garantizarían igual; esto lo hace sin estrellarse.
        Optional<ConfidenceAssessment> viva = quien.esLead()
                ? assessments.findByLeadIdAndLanguageCodeAndStatusAndUserIdIsNull(
                        quien.leadId(), languageCode, AssessmentStatus.IN_PROGRESS)
                : assessments.findByUserIdAndLanguageCodeAndStatus(
                        quien.userId(), languageCode, AssessmentStatus.IN_PROGRESS);
        if (viva.isPresent()) {
            return new Iniciada(viva.get(), abrirVoz(quien, languageCode));
        }

        enfriamiento(quien, languageCode);

        int siguiente = quien.esLead() ? 1 : assessments.lastSequence(quien.userId(), languageCode) + 1;
        ConfidenceAssessment nueva = assessments.saveAndFlush(new ConfidenceAssessment(
                quien.userId(), quien.leadId(), languageCode, siguiente, clock.instant()));
        return new Iniciada(nueva, abrirVoz(quien, languageCode));
    }

    /**
     * El enfriamiento entre diagnósticos. Responde 409 con la fecha exacta: «no puedes» sin
     * «cuándo sí» deja a la persona sin nada que hacer con la información.
     */
    private void enfriamiento(Evaluado quien, String languageCode) {
        int dias = settings.getInt("assessment_cooldown_days");
        Optional<ConfidenceAssessment> ultima = quien.esLead()
                ? assessments.findFirstByLeadIdAndLanguageCodeAndStatusAndUserIdIsNullOrderByCompletedAtDesc(
                        quien.leadId(), languageCode, AssessmentStatus.COMPLETED)
                : assessments.findFirstByUserIdAndLanguageCodeAndStatusOrderByCompletedAtDesc(
                        quien.userId(), languageCode, AssessmentStatus.COMPLETED);
        ultima.filter(u -> u.getCompletedAt() != null)
                .ifPresent(u -> {
                    Instant puedeDesde = u.getCompletedAt().plus(Duration.ofDays(dias));
                    if (clock.instant().isBefore(puedeDesde)) {
                        LocalDate cuando = LocalDate.ofInstant(puedeDesde, BusinessZone.BOGOTA);
                        throw new ConflictException(
                                "Puedes repetir tu diagnóstico a partir del " + cuando
                                        + ". Mientras tanto, tu resultado anterior sigue disponible.");
                    }
                });
    }

    /**
     * Cada llamada entrega una llave nueva del proveedor, y cada llave es una conversación que se
     * paga: por eso el cargo al presupuesto va aquí y no en {@code start}, porque retomar una
     * evaluación viva también abre una sesión. El tope por persona lo pone el controlador.
     */
    private VoiceSession abrirVoz(Evaluado quien, String languageCode) {
        int minutos = settings.getInt("assessment_max_minutes");
        VoiceSession sesion = voice.start(new VoiceSessionRequest(
                languageCode,
                prompts.escenario(languageCode, minutos, quien.nombreDePila()),
                minutos * 60,
                quien.nombreDePila()));
        usage.sesionDeVozAbierta(quien.actorId(), voice.name(), sesion.model(), Duration.ofMinutes(minutos));
        return sesion;
    }

    /* ---------------- Turnos ---------------- */

    /**
     * Registra un turno. El cliente manda lo que solo él sabe; el resto lo deduce el servidor.
     *
     * @param latencyMs  cuánto tardó en abrir la boca, medido en el navegador
     * @param durationMs cuánto habló
     */
    @Transactional
    public void addTurn(Evaluado quien, UUID assessmentId, int turnIndex, String speaker,
                        String transcript, Integer latencyMs, Integer durationMs) {
        ConfidenceAssessment evaluacion = miaYViva(quien, assessmentId);

        AssessmentTurn turno = new AssessmentTurn(
                assessmentId, turnIndex, speaker, transcript, latencyMs, durationMs, clock.instant());

        if (turno.isUser()) {
            boolean cambio = SignalExtractor.nativeSwitch(transcript, evaluacion.getLanguageCode());
            turno.withSignals(
                    SignalExtractor.wordCount(transcript),
                    SignalExtractor.selfCorrections(transcript),
                    SignalExtractor.abandonedClauses(transcript),
                    SignalExtractor.fillerCount(transcript),
                    cambio);
        }
        turns.save(turno);
    }

    /* ---------------- Cerrar ---------------- */

    /**
     * Cierra, calcula y recomienda.
     *
     * <p>Si no hay turnos suficientes, o la conversación se fue a español, se cierra sin número. Lo
     * segundo es una decisión de producto y no una limitación: mostrarle un número bajo a alguien
     * que está empezando desde cero es exactamente lo que Orión no hace.
     *
     * <p><strong>Pero nadie se va con las manos vacías</strong> (Pardo, 22/09/2026). Las tres salidas
     * —con número, en español o demasiado corta— llevan un resumen de lo que contó y tres
     * profesores. Antes, las dos sin número cerraban sin recomendar, y la pantalla le decía a quien
     * acababa de atreverse a hablar «todavía no tenemos tres para ti».
     *
     * <p>El resumen puede ser una llamada a la IA dentro de esta transacción. Está acotada a doce
     * segundos y a esta escala no compite por conexiones; si algún día lo hace, se saca fuera.
     */
    @Transactional
    public ConfidenceAssessment complete(Evaluado quien, UUID assessmentId, List<String> objetivos) {
        ConfidenceAssessment evaluacion = miaYViva(quien, assessmentId);
        List<String> metas = objetivos == null ? List.of() : objetivos;

        List<AssessmentTurn> todos = turns.findByAssessmentIdOrderByTurnIndexAsc(assessmentId);
        List<AssessmentTurn> delUsuario = todos.stream().filter(AssessmentTurn::isUser).toList();

        int segundos = (int) Duration.between(evaluacion.getStartedAt(), clock.instant()).toSeconds();
        String resumen = resumen(quien, metas, todos);
        String nivel;

        // Dos turnos seguidos en el idioma propio: la rama en español. Misma regla que el guion.
        if (seFueAlEspanol(delUsuario)) {
            evaluacion.switchToFromZero();
            evaluacion.abandon(segundos, delUsuario.size());
            evaluacion.summarize(resumen);
            nivel = "BEGINNER";
        } else {
            List<TurnoDelUsuario> senales = delUsuario.stream().map(AssessmentTurn::asSignal).toList();
            Optional<Puntaje> puntaje = new ConfidenceScoreCalculator().calcular(senales, pesos());
            if (puntaje.isEmpty()) {
                evaluacion.abandon(segundos, delUsuario.size());
                evaluacion.summarize(resumen);
                nivel = null;
            } else {
                evaluacion.complete(puntaje.get(),
                        aJson(puntaje.get().dimensiones()),
                        aJson(puntaje.get().observadas().stream().map(Enum::name).toList()),
                        resumen,
                        segundos, delUsuario.size(), clock.instant());
                nivel = nivelInferido(puntaje.get().valor());
            }
        }

        ConfidenceAssessment guardada = assessments.save(evaluacion);
        List<Recomendacion> tres = recommender.para(evaluacion.getLanguageCode(), nivel, metas);
        recommendations.saveAll(tres.stream()
                .map(r -> new AssessmentRecommendation(assessmentId, r)).toList());
        return guardada;
    }

    /**
     * Lo que contó y por qué Orión le sirve para eso. Lo escribe la IA si hay presupuesto y la
     * salida pasa la revisión; si no, la plantilla. El resultado nunca espera por esto.
     */
    private String resumen(Evaluado quien, List<String> metas, List<AssessmentTurn> todos) {
        String nombre = quien.nombreDePila();
        Map<String, String> nombreDeMeta = objetivosDelCatalogo.findByActiveTrueOrderByDisplayOrderAsc()
                .stream().collect(Collectors.toMap(TeachingGoal::getCode, TeachingGoal::getNameEs,
                        (a, b) -> a));
        List<String> nombres = metas.stream().map(nombreDeMeta::get)
                .filter(n -> n != null && !n.isBlank()).toList();

        if (budget.disponible()) {
            Optional<String> escrito = resumidor.resumir(new ConversationSummarizer.Pedido(
                    quien.actorId(), nombre, nombres,
                    todos.stream()
                            .map(t -> new ConversationSummarizer.Turno(!t.isUser(), t.getTranscript()))
                            .toList()));
            if (escrito.isPresent()) {
                return escrito.get();
            }
        }
        return ResumenDePlantilla.para(nombre, nombres);
    }

    /**
     * La traducción al español de una frase de Meissa. Solo para el dueño de un diagnóstico vivo:
     * es una llamada que se paga, y abierta a cualquiera sería un traductor gratis con nuestra
     * llave. Sin presupuesto no se traduce —la conversación sigue igual, sin la línea de abajo—.
     */
    @Transactional(readOnly = true)
    public Optional<String> traducir(Evaluado quien, UUID assessmentId, String frase) {
        miaYViva(quien, assessmentId);
        if (IdiomaDeLaFrase.pareceEspanol(frase) || !budget.disponible()) {
            return Optional.empty();
        }
        return traductor.alEspanol(quien.actorId(), frase);
    }

    @Transactional
    public void abandon(Evaluado quien, UUID assessmentId) {
        ConfidenceAssessment evaluacion = miaYViva(quien, assessmentId);
        int segundos = (int) Duration.between(evaluacion.getStartedAt(), clock.instant()).toSeconds();
        evaluacion.abandon(segundos, (int) turns.countByAssessmentId(assessmentId));
        assessments.save(evaluacion);
    }

    /* ---------------- Lecturas ---------------- */

    @Transactional(readOnly = true)
    public ConfidenceAssessment mia(Evaluado quien, UUID assessmentId) {
        return assessments.findById(assessmentId)
                .filter(quien::esDuenoDe)
                // 404 y no 403: la evaluación de otro no existe para ti.
                .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<ConfidenceAssessment> history(Evaluado quien) {
        return quien.esLead()
                ? assessments.findByLeadIdAndUserIdIsNullOrderByStartedAtDesc(quien.leadId())
                : assessments.findByUserIdOrderByStartedAtDesc(quien.userId());
    }

    @Transactional(readOnly = true)
    public List<AssessmentRecommendation> recommendationsOf(UUID assessmentId) {
        return recommendations.findByIdAssessmentIdOrderByPositionAsc(assessmentId);
    }

    @Transactional(readOnly = true)
    public List<AssessmentTurn> turnsOf(UUID assessmentId) {
        return turns.findByAssessmentIdOrderByTurnIndexAsc(assessmentId);
    }

    /* ---------------- Interno ---------------- */

    private ConfidenceAssessment miaYViva(Evaluado quien, UUID assessmentId) {
        ConfidenceAssessment evaluacion = mia(quien, assessmentId);
        if (!evaluacion.isLive()) {
            throw new UnprocessableException("Este diagnóstico ya está cerrado.");
        }
        return evaluacion;
    }

    /** Dos turnos seguidos en el idioma propio. Uno solo no basta: nadie se rinde por una frase. */
    private boolean seFueAlEspanol(List<AssessmentTurn> delUsuario) {
        for (int i = 1; i < delUsuario.size(); i++) {
            if (delUsuario.get(i).isNativeSwitch() && delUsuario.get(i - 1).isNativeSwitch()) {
                return true;
            }
        }
        return false;
    }

    private PesosDelPuntaje pesos() {
        return new PesosDelPuntaje(
                settings.getInt("score_weight_arranque"),
                settings.getInt("score_weight_continuidad"),
                settings.getInt("score_weight_extension"),
                settings.getInt("score_weight_autonomia"),
                settings.getInt("score_weight_soltura"));
    }

    /**
     * El nivel que se le pasa al buscador de profesores. No es un nivel del MCER y no se le muestra
     * a la persona como tal: es solo el filtro con el que se eligen los tres nombres.
     */
    private static String nivelInferido(int puntaje) {
        if (puntaje >= 75) {
            return "ADVANCED";
        }
        return puntaje >= 45 ? "INTERMEDIATE" : "BEGINNER";
    }

    private static String aJson(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el resultado del diagnóstico", ex);
        }
    }

    /** Lo que devuelve empezar: la evaluación abierta y con qué conectarse. */
    public record Iniciada(ConfidenceAssessment assessment, VoiceSession voice) {
    }
}
