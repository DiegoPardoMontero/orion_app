package co.orion.lifecycle.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * La última corrida de cada job programado, para que el admin pueda verlo.
 *
 * Existe por una razón concreta: un job que no corre es indistinguible de uno que corre y no
 * encuentra nada que hacer. Y el de autocompletado es el que le paga a los profesores — si se
 * detiene, el síntoma llega semanas después en forma de "no me han pagado".
 *
 * En memoria a propósito: a este volumen basta con saber si el proceso vivo está haciendo su
 * trabajo, y una tabla más solo añadiría escrituras. Se pierde al reiniciar, y eso también informa
 * (un registro vacío en un proceso que lleva horas arriba es una señal).
 */
@Component
public class JobRunRegistry {

    public record JobRun(String job, Instant at, boolean ok, String detail) {
    }

    private final Map<String, JobRun> lastRuns = new ConcurrentHashMap<>();

    public void recordSuccess(String job, Instant at, String detail) {
        lastRuns.put(job, new JobRun(job, at, true, detail));
    }

    public void recordFailure(String job, Instant at, String detail) {
        lastRuns.put(job, new JobRun(job, at, false, detail));
    }

    public List<JobRun> all() {
        return lastRuns.values().stream()
                .sorted((a, b) -> a.job().compareTo(b.job()))
                .toList();
    }
}
