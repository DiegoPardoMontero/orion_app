package co.orion.identity.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * La tarifa vista desde administración, que admite el 0 de una clase gratuita — el propio profesor
 * no puede ponerlo ({@link RateRequest} mantiene el piso de 20.000) para que nadie regale su
 * trabajo por un descuido al teclear.
 *
 * El hueco entre 1 y 19.999 lo cierra el CHECK de la base, no esta anotación: una tarifa así queda
 * además por debajo del mínimo que acepta la pasarela.
 */
public record AdminRateRequest(
        @NotNull @Min(0) @Max(500000) Long hourlyRateCop) {
}
