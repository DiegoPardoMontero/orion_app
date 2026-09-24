package co.orion.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.identity.domain.FichaPorCompletarEvent;

/**
 * Los recordatorios de completar la ficha (decisión de Pardo, 24/09/2026): relativamente
 * insistentes, pero con fin. Al día 1 de la cuenta, un aviso en la app; al día 2, un correo; al día
 * 3, otro aviso. Y nada más: la franja de Rigel sigue en pantalla hasta que la complete, pero la
 * campana y el correo no persiguen para siempre.
 *
 * <p>Cada paso sale una vez (llave primaria en {@code profile_reminders}), y entre uno y el
 * siguiente pasan al menos 20 horas: una cuenta vieja que llega a este job no recibe los tres el
 * mismo día. La transacción va por {@code TransactionTemplate}: el programador llama a {@link #run()},
 * y una anotación en el método interno no se aplicaría.
 */
@Component
public class RecordatorioDeFicha {

    /** A qué edad de la cuenta sale cada paso. */
    private static final Map<Integer, Duration> DESDE = Map.of(
            1, Duration.ofDays(1), 2, Duration.ofDays(2), 3, Duration.ofDays(3));
    private static final Duration ENTRE_PASOS = Duration.ofHours(20);

    private static final Map<String, String> EN_PALABRAS = Map.of(
            "FOTO", "tu foto", "NIVEL", "tu nivel", "IDIOMA", "el idioma",
            "OBJETIVO", "para qué lo aprendes", "MOTIVACION", "tu motivación");

    private final JdbcTemplate jdbc;
    private final StudentProfileService fichas;
    private final FichaMailer correo;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate enTransaccion;
    private final Clock clock;

    public RecordatorioDeFicha(JdbcTemplate jdbc, StudentProfileService fichas, FichaMailer correo,
                               ApplicationEventPublisher eventos, PlatformTransactionManager transacciones,
                               Clock clock) {
        this.jdbc = jdbc;
        this.fichas = fichas;
        this.correo = correo;
        this.eventos = eventos;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.ficha-reminder.interval-ms:3600000}")
    public void run() {
        recordar();
    }

    /** @return cuántos recordatorios salieron */
    public int recordar() {
        Instant ahora = clock.instant();
        // Estudiantes activos con al menos un día de cuenta y algún paso pendiente.
        List<Object[]> candidatos = jdbc.query("""
                select u.id, u.email, u.full_name, u.created_at,
                       (select max(step) from profile_reminders r where r.user_id = u.id),
                       (select max(sent_at) from profile_reminders r where r.user_id = u.id)
                  from users u
                 where u.role = 'STUDENT' and u.status = 'ACTIVE' and u.created_at < ?
                   and (select count(*) from profile_reminders r where r.user_id = u.id) < 3
                """, (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant(), rs.getObject(5), rs.getTimestamp(6)},
                Timestamp.from(ahora.minus(DESDE.get(1))));
        int enviados = 0;
        for (Object[] c : candidatos) {
            UUID id = (UUID) c[0];
            int ultimo = c[4] == null ? 0 : ((Number) c[4]).intValue();
            int paso = ultimo + 1;
            Instant creada = (Instant) c[3];
            Instant anterior = c[5] == null ? null : ((Timestamp) c[5]).toInstant();
            if (creada.plus(DESDE.get(paso)).isAfter(ahora)
                    || (anterior != null && anterior.plus(ENTRE_PASOS).isAfter(ahora))) {
                continue;
            }
            List<String> faltan = fichas.faltanDeLaFicha(id).stream().map(EN_PALABRAS::get).toList();
            if (faltan.isEmpty()) {
                continue;
            }
            Boolean salio = enTransaccion.execute(estado -> {
                int filas = jdbc.update("""
                        insert into profile_reminders (user_id, step, sent_at) values (?, ?, ?)
                        on conflict do nothing
                        """, id, paso, Timestamp.from(ahora));
                if (filas == 0) {
                    return false;
                }
                eventos.publishEvent(new FichaPorCompletarEvent(id, paso, faltan));
                return true;
            });
            if (Boolean.TRUE.equals(salio)) {
                enviados++;
                if (paso == 2) {
                    correo.recordar((String) c[1], (String) c[2], faltan);
                }
            }
        }
        return enviados;
    }
}
