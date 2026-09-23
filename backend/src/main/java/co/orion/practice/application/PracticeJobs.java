package co.orion.practice.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Los dos trabajos de la práctica: generar lo pendiente y dejar de ofrecer lo vencido. */
@Component
public class PracticeJobs {

    private static final Logger log = LoggerFactory.getLogger(PracticeJobs.class);

    private final PracticeService practica;

    public PracticeJobs(PracticeService practica) {
        this.practica = practica;
    }

    /** Cada minuto: el estudiante que acaba de leer su acta encuentra la práctica casi enseguida. */
    @Scheduled(fixedDelayString = "${orion.jobs.practice-generation.interval-ms:60000}")
    public void generar() {
        int listos = practica.generarPendientes();
        if (listos > 0) {
            log.info("Práctica: {} set(s) listos", listos);
        }
    }

    @Scheduled(fixedDelayString = "${orion.jobs.practice-expiry.interval-ms:3600000}")
    public void expirar() {
        int vencidos = practica.expirarVencidos();
        if (vencidos > 0) {
            log.info("Práctica: {} set(s) vencidos", vencidos);
        }
    }
}
