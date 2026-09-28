import { describe, expect, it } from "vitest";
import { conMiles, posicionTrasCifras, soloDigitos } from "./tarifa";

describe("soloDigitos", () => {
  it("lee la tarifa como la escribe un colombiano", () => {
    for (const escrito of ["50.000", "50,000", "50 000", "$50.000", "50000", " 50.000 "]) {
      expect(soloDigitos(escrito)).toBe("50000");
    }
  });

  it("sin ceros a la izquierda, pero el cero solo se queda", () => {
    expect(soloDigitos("050.000")).toBe("50000");
    expect(soloDigitos("0")).toBe("0");
    expect(soloDigitos("")).toBe("");
    expect(soloDigitos("abc")).toBe("");
  });

  it("no deja crecer el número sin fin", () => {
    expect(soloDigitos("1234567890123")).toHaveLength(9);
  });
});

describe("conMiles", () => {
  it("pone el punto de miles", () => {
    expect(conMiles("")).toBe("");
    expect(conMiles("0")).toBe("0");
    expect(conMiles("500")).toBe("500");
    expect(conMiles("5000")).toBe("5.000");
    expect(conMiles("50000")).toBe("50.000");
    expect(conMiles("500000")).toBe("500.000");
    expect(conMiles("1500000")).toBe("1.500.000");
  });
});

describe("posicionTrasCifras", () => {
  it("deja el cursor detrás de las mismas cifras aunque aparezcan puntos", () => {
    expect(posicionTrasCifras("50.000", 0)).toBe(0);
    expect(posicionTrasCifras("50.000", 2)).toBe(2);
    expect(posicionTrasCifras("50.000", 3)).toBe(4);
    expect(posicionTrasCifras("50.000", 5)).toBe(6);
    expect(posicionTrasCifras("50.000", 9)).toBe(6);
  });
});
