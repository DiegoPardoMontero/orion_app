import { describe, expect, it } from "vitest";
import { ayudaDeTarifa, estadoDeFundador, recibe } from "./fundador";

// El 12 de enero de 2027 a las 00:00 de Bogotá.
const HASTA = "2027-01-12T05:00:00Z";

describe("lo que recibe el profe", () => {
  it("redondea la comisión hacia abajo al peso, como el backend", () => {
    expect(recibe(60_000, 1500)).toBe(51_000);
    expect(recibe(60_000, 2000)).toBe(48_000);
    // 45.555 × 15 % = 6.833,25 → 6.833 de comisión: el cuarto de peso queda para el profe.
    expect(recibe(45_555, 1500)).toBe(38_722);
    expect(recibe(33_333, 2000)).toBe(26_667);
  });
});

describe("la ayuda de la tarifa", () => {
  it("fundador sin empezar", () => {
    expect(ayudaDeTarifa(60_000, 2000, { rateBps: 1500, periodMonths: 3, status: "NOT_STARTED" })).toBe(
      "Recibes $51.000 por clase: 15 % de comisión como profe fundador durante tus primeros 3 meses de clases. Después recibirás $48.000 (20 %).",
    );
  });

  it("fundador activo, con la fecha en Bogotá", () => {
    expect(ayudaDeTarifa(60_000, 2000, { rateBps: 1500, periodMonths: 3, status: "ACTIVE", until: HASTA })).toBe(
      "Recibes $51.000 por clase: 15 % de comisión como profe fundador hasta el 12 de enero de 2027. Después recibirás $48.000 (20 %).",
    );
  });

  it("no fundador o ya terminó", () => {
    expect(ayudaDeTarifa(60_000, 2000, null)).toBe("Recibes $48.000 por clase (comisión de Orión: 20 %).");
    expect(ayudaDeTarifa(60_000, 2000, { rateBps: 1500, periodMonths: 3, status: "ENDED", until: HASTA })).toBe(
      "Recibes $48.000 por clase (comisión de Orión: 20 %).",
    );
  });

  it("la clase gratis no paga comisión", () => {
    expect(ayudaDeTarifa(0, 2000, { rateBps: 1500, periodMonths: 3, status: "ACTIVE", until: HASTA })).toBe(
      "Clase gratis: no pagas comisión.",
    );
  });
});

describe("el estado que ve el admin", () => {
  it("los cuatro", () => {
    expect(estadoDeFundador(null)).toBe("Sin beneficio de fundador");
    expect(estadoDeFundador({ rateBps: 1500, status: "NOT_STARTED" })).toBe("Fundador · 15 % · sin empezar");
    expect(estadoDeFundador({ rateBps: 1500, status: "ACTIVE", until: HASTA })).toBe(
      "Fundador · 15 % hasta el 12 de enero de 2027",
    );
    expect(estadoDeFundador({ rateBps: 1500, status: "ENDED", until: HASTA })).toBe(
      "Fundador · terminó el 12 de enero de 2027",
    );
  });
});
