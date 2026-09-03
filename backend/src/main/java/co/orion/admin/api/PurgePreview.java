package co.orion.admin.api;

import java.util.List;

/**
 * Qué se va a destruir, ANTES de destruirlo.
 *
 * Existe porque un borrado definitivo no se puede deshacer y la mitad de los borrados peligrosos
 * ocurren porque quien los ejecuta no sabía todo lo que colgaba de lo que estaba borrando. Aquí lo
 * ve: cuántas filas de cada cosa, y sobre todo cuánto dinero se lleva por delante.
 */
public record PurgePreview(String target,
                           String label,
                           List<Row> rows,
                           Money money,
                           List<String> warnings) {

    public record Row(String what, long count) {
    }

    /**
     * El dinero que desaparece con el borrado. {@code settledCop} es el que YA se le transfirió a
     * un profesor: borrarlo deja la contabilidad sin su respaldo.
     */
    public record Money(long paymentsCop, long settledCop, long creditsCop) {

        public boolean isEmpty() {
            return paymentsCop == 0 && settledCop == 0 && creditsCop == 0;
        }
    }
}
