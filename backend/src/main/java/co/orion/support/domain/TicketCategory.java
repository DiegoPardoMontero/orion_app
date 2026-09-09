package co.orion.support.domain;

import java.time.Instant;

import co.orion.shared.time.PlazoLegal;
import java.util.Optional;

/**
 * De qué va una solicitud. Tres de estas categorías tienen plazo de respuesta fijado por ley y no
 * por nosotros: son, además, el canal de derechos del titular que exige la Ley 1581. No hace falta
 * un formulario aparte de habeas data — hace falta que este exista y responda a tiempo.
 */
public enum TicketCategory {

    /** Art. 14 de la Ley 1581 de 2012: 10 días hábiles, prorrogables por 5 más. */
    HABEAS_DATA_CONSULTA("Consulta sobre mis datos personales", Plazo.habiles(10)),

    /** Art. 15 de la Ley 1581 de 2012: 15 días hábiles, prorrogables por 8 más. */
    HABEAS_DATA_RECLAMO("Reclamo sobre mis datos personales", Plazo.habiles(15)),

    /** Art. 47 de la Ley 1480 de 2011, con el plazo de la Ley 2439 de 2024. */
    RETRACTO("Quiero retractarme de una compra", Plazo.calendario(15)),

    PAGO("Un pago o una devolución", Plazo.ninguno()),
    CLASE("Un problema con una clase", Plazo.ninguno()),
    CUENTA("Mi cuenta o mi perfil", Plazo.ninguno()),
    OTRO("Otra cosa", Plazo.ninguno());

    private final String etiqueta;
    private final Plazo plazo;

    TicketCategory(String etiqueta, Plazo plazo) {
        this.etiqueta = etiqueta;
        this.plazo = plazo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Si la ley fija un plazo, cuándo vence. Vacío si no lo fija. */
    public Optional<Instant> vencimientoDesde(Instant creacion) {
        return plazo.vencimiento(creacion);
    }

    public boolean tienePlazoLegal() {
        return plazo.dias() > 0;
    }

    private record Plazo(int dias, boolean habiles) {

        static Plazo habiles(int dias) {
            return new Plazo(dias, true);
        }

        static Plazo calendario(int dias) {
            return new Plazo(dias, false);
        }

        static Plazo ninguno() {
            return new Plazo(0, false);
        }

        Optional<Instant> vencimiento(Instant creacion) {
            if (dias == 0) {
                return Optional.empty();
            }
            return Optional.of(habiles
                    ? PlazoLegal.sumandoDiasHabiles(creacion, dias)
                    : PlazoLegal.sumandoDiasCalendario(creacion, dias));
        }
    }
}
