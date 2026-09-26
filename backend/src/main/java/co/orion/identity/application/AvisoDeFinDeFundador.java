package co.orion.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.FounderEndingEvent;
import co.orion.shared.observability.JobRunRegistry;

/**
 * El aviso de que el beneficio de profe fundador termina (brief del profe fundador, paso 4): 14 días
 * antes de {@code founder_until}, en la campana y por correo, una sola vez. El «una sola vez» lo
 * decide la base: el UPDATE que marca {@code founder_expiry_notified_at} solo gana si nadie lo marcó,
 * y el aviso de la campana se guarda en esa misma transacción (antes del commit), así que si uno falla
 * no queda ninguno y se reintenta en la corrida siguiente.
 *
 * <p>No avisa a quien no es fundador ni a quien no ha empezado el conteo: sin fecha de fin no hay
 * nada que anunciar. Lo vigila {@code JobWatchdog}.
 */
@Component
public class AvisoDeFinDeFundador {

    public static final String JOB = "founder-expiry";
    private static final Logger log = LoggerFactory.getLogger(AvisoDeFinDeFundador.class);
    private static final Duration ANTES = Duration.ofDays(14);

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;
    private final FounderMailer correo;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate enTransaccion;
    private final JobRunRegistry runs;
    private final Clock clock;

    public AvisoDeFinDeFundador(JdbcTemplate jdbc, PlatformSettingsService settings, FounderMailer correo,
                                ApplicationEventPublisher eventos, PlatformTransactionManager transacciones,
                                JobRunRegistry runs, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.correo = correo;
        this.eventos = eventos;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.runs = runs;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.founder-expiry.interval-ms:3600000}")
    public void run() {
        try {
            int avisos = avisar();
            runs.recordSuccess(JOB, clock.instant(), avisos + " aviso(s)");
        } catch (RuntimeException ex) {
            runs.recordFailure(JOB, clock.instant(), ex.getMessage());
            throw ex;
        }
    }

    /** @return cuántos avisos salieron */
    public int avisar() {
        Instant ahora = clock.instant();
        record Candidato(UUID id, String email, String nombre, int rateBps, Instant until) {
        }
        List<Candidato> candidatos = jdbc.query("""
                select p.user_id, u.email, u.full_name, p.founder_rate_bps, p.founder_until
                  from professor_profiles p
                  join users u on u.id = p.user_id
                 where p.founder_until is not null
                   and p.founder_rate_bps is not null
                   and p.founder_expiry_notified_at is null
                   and p.founder_until > ?
                   and p.founder_until <= ?
                """, (rs, i) -> new Candidato(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getTimestamp(5).toInstant()),
                Timestamp.from(ahora), Timestamp.from(ahora.plus(ANTES)));

        int base = settings.getInt("commission_rate_bps");
        int enviados = 0;
        for (Candidato c : candidatos) {
            Boolean salio;
            try {
                salio = enTransaccion.execute(estado -> {
                    int filas = jdbc.update("""
                            update professor_profiles set founder_expiry_notified_at = ?
                             where user_id = ? and founder_expiry_notified_at is null
                            """, Timestamp.from(ahora), c.id());
                    if (filas == 0) {
                        return false;
                    }
                    eventos.publishEvent(new FounderEndingEvent(c.id(), c.rateBps(), base, c.until()));
                    return true;
                });
            } catch (RuntimeException ex) {
                // La marca y el aviso de la campana van en la misma transacción: si algo falla, no
                // queda ninguno y la siguiente corrida lo reintenta. Los demás profes siguen.
                log.warn("No se pudo avisar el fin del beneficio de fundador a {}: {}", c.id(), ex.getMessage());
                continue;
            }
            if (Boolean.TRUE.equals(salio)) {
                enviados++;
                correo.avisarFin(c.email(), c.nombre(), c.rateBps(), base, c.until());
            }
        }
        return enviados;
    }
}
