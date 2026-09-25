import { describe, expect, it } from "vitest";
import { ESTADOS_DE_PAGO, estadoDePago, PARA_EL_ADMIN, PARA_EL_ESTUDIANTE, PARA_EL_PROFESOR } from "./estadosDePago";

describe("cada quien ve el estado de un pago en palabras", () => {
  it.each([
    ["el estudiante", PARA_EL_ESTUDIANTE],
    ["el profesor", PARA_EL_PROFESOR],
    ["el admin", PARA_EL_ADMIN],
  ])("%s tiene texto para todos los estados del backend", (_quien, tabla) => {
    for (const estado of ESTADOS_DE_PAGO) {
      expect(tabla[estado], estado).toBeDefined();
      expect(tabla[estado].texto).not.toMatch(/^[A-Z_]+$/);
    }
  });

  it("el profesor distingue lo que ya va en una liquidación", () => {
    expect(estadoDePago(PARA_EL_PROFESOR, "IN_TRANSIT").texto).toBe("En camino");
    expect(estadoDePago(PARA_EL_PROFESOR, "TRANSFERRED").texto).toBe("Transferido");
  });

  it("un estado que todavía no conoce sale tal cual antes que desaparecer", () => {
    expect(estadoDePago(PARA_EL_ADMIN, "NUEVO")).toEqual({ texto: "NUEVO", tono: "neutral" });
  });
});
