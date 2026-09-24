package co.orion.teaching.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.time.BusinessZone;

/**
 * Las cifras que dicen si el acta sirve (brief del Bloque 10, paso C1).
 *
 * <p>La que manda es la distribución de {@code edit_ratio}: <strong>si más de la mitad de las
 * actas se reescriben por completo, la función se revisa antes de ampliarla</strong>. Los cortes
 * son los del brief: sin editar (hasta 0,05, un retoque de puntuación), edición menor, y
 * reescrita desde 0,5 —más de la mitad del texto cambió—.
 *
 * <p>Todo cuenta desde que existen las actas (la V48) y a lo sumo {@value #DIAS} días atrás: una
 * clase de antes no podía tener acta, y contarla bajaría el porcentaje sin decir nada.
 */
@Service
public class LessonNoteMetrics {

    static final int DIAS = 30;
    static final int DIAS_DEL_PROFESOR = 90;
    static final double SIN_EDITAR = 0.05;
    static final double REESCRITA = 0.5;

    /**
     * Las clases de prueba no cuentan: un ensayo del acta desde Sistema no puede mover el porcentaje
     * de nadie. El gasto y los resultados de la IA sí incluyen los ensayos, porque son llamadas reales.
     */
    private static final String SIN_ENSAYOS = " and booking_id not in (select id from bookings where is_rehearsal)";

    private final JdbcTemplate jdbc;
    private final TeachingAiBudget presupuesto;
    private final PlatformSettingsService settings;
    private final LessonNotesLaunch lanzamiento;
    private final Clock clock;

    public LessonNoteMetrics(JdbcTemplate jdbc, TeachingAiBudget presupuesto, PlatformSettingsService settings,
                             LessonNotesLaunch lanzamiento, Clock clock) {
        this.jdbc = jdbc;
        this.presupuesto = presupuesto;
        this.settings = settings;
        this.lanzamiento = lanzamiento;
        this.clock = clock;
    }

    public record Panel(long generadasHoy, long publicadasHoy, long clasesCerradas, long clasesConActa,
                        long sinEditar, long edicionMenor, long reescritas, long gastadoHoyCop, long topeCop,
                        boolean iaEncendida, Map<String, Long> resultadosHoy) {
    }

    /** Lo que ve el admin en su panel. */
    @Transactional(readOnly = true)
    public Panel panel() {
        Instant hoy = inicioDeHoy();
        Instant manana = hoy.plus(Duration.ofDays(1));
        Instant desde = desde(DIAS);

        long generadas = contar("select count(*) from lesson_notes where created_at >= ? and created_at < ?" + SIN_ENSAYOS, hoy, manana);
        long publicadas = contar("select count(*) from lesson_notes where published_at >= ? and published_at < ?" + SIN_ENSAYOS, hoy, manana);
        long cerradas = contar("select count(*) from bookings where status = 'COMPLETED' and completed_at >= ? and not is_rehearsal", desde);
        long conActa = contar("""
                select count(*) from bookings b join lesson_notes n on n.booking_id = b.id
                where b.status = 'COMPLETED' and b.completed_at >= ? and n.status = 'PUBLISHED'
                  and not b.is_rehearsal
                """, desde);
        long sinEditar = contar("""
                select count(*) from lesson_notes
                where published_at >= ? and edit_ratio is not null and edit_ratio <= ?
                """ + SIN_ENSAYOS, desde, SIN_EDITAR);
        long reescritas = contar("""
                select count(*) from lesson_notes
                where published_at >= ? and edit_ratio is not null and edit_ratio >= ?
                """ + SIN_ENSAYOS, desde, REESCRITA);
        long conRatio = contar("select count(*) from lesson_notes where published_at >= ? and edit_ratio is not null" + SIN_ENSAYOS, desde);

        Map<String, Long> resultados = new LinkedHashMap<>();
        jdbc.query("""
                select outcome, count(*) from ai_usage_log
                where feature = ? and occurred_at >= ? and occurred_at < ?
                group by outcome order by outcome
                """, rs -> {
            resultados.put(rs.getString(1), rs.getLong(2));
        }, TeachingAiBudget.FEATURE, Timestamp.from(hoy), Timestamp.from(manana));

        return new Panel(generadas, publicadas, cerradas, conActa, sinEditar, conRatio - sinEditar - reescritas,
                reescritas, presupuesto.gastadoHoy(), settings.getInt("ai_daily_budget_cop"),
                settings.getBoolean("ai_lesson_notes_enabled"), resultados);
    }

    public record DelProfesor(long clasesCerradas, long clasesConActa, int dias) {
    }

    /** Lo que ve el profesor en su desempeño. Informativo: no alimenta el ranking ni las sanciones. */
    @Transactional(readOnly = true)
    public DelProfesor delProfesor(UUID profesorId) {
        Instant desde = desde(DIAS_DEL_PROFESOR);
        long cerradas = contar("""
                select count(*) from bookings
                where professor_id = ? and status = 'COMPLETED' and completed_at >= ? and not is_rehearsal
                """, profesorId, desde);
        long conActa = contar("""
                select count(*) from bookings b join lesson_notes n on n.booking_id = b.id
                where b.professor_id = ? and b.status = 'COMPLETED' and b.completed_at >= ?
                  and n.status = 'PUBLISHED' and not b.is_rehearsal
                """, profesorId, desde);
        return new DelProfesor(cerradas, conActa, DIAS_DEL_PROFESOR);
    }

    private Instant inicioDeHoy() {
        return LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA).atStartOfDay(BusinessZone.BOGOTA).toInstant();
    }

    /** Hace {@code dias} días, o desde que existen las actas si es más reciente. */
    private Instant desde(int dias) {
        Instant ventana = clock.instant().minus(Duration.ofDays(dias));
        Instant inicio = lanzamiento.desde();
        return inicio.isAfter(ventana) ? inicio : ventana;
    }

    private long contar(String sql, Object... args) {
        Object[] convertidos = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertidos[i] = args[i] instanceof Instant instante ? Timestamp.from(instante) : args[i];
        }
        Long n = jdbc.queryForObject(sql, Long.class, convertidos);
        return n == null ? 0 : n;
    }
}
