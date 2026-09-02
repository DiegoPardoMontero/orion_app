/**
 * Cuánto saldo se aplica de verdad a una clase, para poder anticipar el desglose antes de reservar.
 *
 * Replica la regla del backend (`CreditService.applyTo`), incluido su ajuste menos obvio: si el
 * saldo dejara un resto por cobrar POR DEBAJO del mínimo de la pasarela, se aplica menos saldo para
 * que el resto llegue justo a ese mínimo. Sin replicarla, la pantalla prometía "Total a pagar
 * $1.000" y el checkout de Wompi pedía $1.500 — la peor forma de perder la confianza de alguien es
 * cambiarle el precio entre una pantalla y la siguiente.
 *
 * Quien manda sigue siendo el backend: esto es una estimación, y la cifra que vale es la que
 * devuelve al crear la reserva.
 */

/** Cobro mínimo que acepta Wompi. Espejo de `WompiPaymentProvider.MIN_CHARGE_COP`. */
export const COBRO_MINIMO_COP = 1500;

export type DesgloseSaldo = { creditoAplicadoCop: number; aPagarCop: number };

export function aplicarSaldo(precioCop: number, saldoCop: number): DesgloseSaldo {
  if (precioCop <= 0 || saldoCop <= 0) {
    return { creditoAplicadoCop: 0, aPagarCop: Math.max(precioCop, 0) };
  }

  let aplicado = Math.min(saldoCop, precioCop);
  const resto = precioCop - aplicado;
  if (resto > 0 && resto < COBRO_MINIMO_COP) {
    aplicado = precioCop - COBRO_MINIMO_COP;
  }
  if (aplicado <= 0) {
    return { creditoAplicadoCop: 0, aPagarCop: precioCop };
  }
  return { creditoAplicadoCop: aplicado, aPagarCop: precioCop - aplicado };
}
