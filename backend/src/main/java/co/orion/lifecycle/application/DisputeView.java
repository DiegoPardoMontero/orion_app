package co.orion.lifecycle.application;

import java.time.Instant;

import co.orion.lifecycle.domain.Dispute;

/** Un reclamo con lo que hace falta para juzgarlo: cuándo era la clase, quiénes y cuánto dinero. */
public record DisputeView(Dispute dispute,
                          Instant classAt,
                          String studentName,
                          String professorName,
                          long amountCop) {
}
