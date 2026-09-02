import { describe, expect, it } from "vitest";
import { aplicarSaldo, COBRO_MINIMO_COP } from "./saldo";

describe("aplicarSaldo", () => {
  it("sin saldo, se paga la clase entera", () => {
    expect(aplicarSaldo(60000, 0)).toEqual({ creditoAplicadoCop: 0, aPagarCop: 60000 });
  });

  it("con saldo parcial, se descuenta lo que hay", () => {
    expect(aplicarSaldo(60000, 20000)).toEqual({ creditoAplicadoCop: 20000, aPagarCop: 40000 });
  });

  it("con saldo de sobra, la clase queda cubierta y no hay nada que cobrar", () => {
    expect(aplicarSaldo(60000, 90000)).toEqual({ creditoAplicadoCop: 60000, aPagarCop: 0 });
  });

  it("nunca deja un resto por debajo del mínimo de la pasarela", () => {
    // 59.900 de saldo sobre una clase de 60.000 dejaría un cobro de 100, que Wompi no acepta.
    // Se aplica menos saldo y el estudiante conserva la diferencia.
    const desglose = aplicarSaldo(60000, 59900);
    expect(desglose.aPagarCop).toBe(COBRO_MINIMO_COP);
    expect(desglose.creditoAplicadoCop).toBe(60000 - COBRO_MINIMO_COP);
  });

  it("el saldo exacto sí cubre la clase entera: no hay resto que rescatar", () => {
    expect(aplicarSaldo(60000, 60000)).toEqual({ creditoAplicadoCop: 60000, aPagarCop: 0 });
  });
});
