import { describe, expect, it } from "vitest";
import { RECORRIDO_ESTUDIANTE, RECORRIDO_PROFESOR, ubicarTarjeta } from "./recorrido";

const pantalla = { width: 1280, height: 800 };
const tarjeta = { width: 340, height: 200 };

describe("ubicarTarjeta", () => {
  it("abajo del foco cuando cabe, alineada con él", () => {
    const r = ubicarTarjeta({ top: 100, left: 300, width: 200, height: 40 }, tarjeta, pantalla);
    expect(r).toEqual({ top: 154, left: 230, lugar: "abajo" });
  });

  it("arriba cuando el foco está al fondo (la barra inferior del móvil)", () => {
    const movil = { width: 390, height: 844 };
    const r = ubicarTarjeta({ top: 776, left: 10, width: 70, height: 68 }, tarjeta, movil);
    expect(r.lugar).toBe("arriba");
    expect(r.top).toBe(776 - 14 - 200);
    // Nunca pegada al borde: el margen de 16 px manda aunque el foco esté en la esquina.
    expect(r.left).toBe(16);
  });

  it("al lado cuando no cabe ni arriba ni abajo (un foco alto en la barra lateral)", () => {
    const r = ubicarTarjeta({ top: 20, left: 16, width: 216, height: 760 }, tarjeta, pantalla);
    expect(r.lugar).toBe("derecha");
    expect(r.left).toBe(16 + 216 + 14);
  });

  it("a la derecha de un ítem de la barra lateral, para no tapar a sus vecinos", () => {
    const r = ubicarTarjeta({ top: 234, left: 10, width: 227, height: 52 }, tarjeta, pantalla);
    expect(r).toEqual({ top: 222, left: 10 + 227 + 14, lugar: "derecha" });
  });

  it("en el móvil, la primera pestaña no cabe al lado: va arriba", () => {
    const r = ubicarTarjeta({ top: 776, left: 0, width: 78, height: 68 }, tarjeta, { width: 390, height: 844 });
    expect(r.lugar).toBe("arriba");
  });

  it("centrada si no hay foco", () => {
    expect(ubicarTarjeta(null, tarjeta, pantalla)).toEqual({ top: 300, left: 470, lugar: "centro" });
  });

  it("en una pantalla angosta nunca se sale por la derecha", () => {
    const r = ubicarTarjeta({ top: 100, left: 360, width: 20, height: 20 }, tarjeta, { width: 390, height: 844 });
    expect(r.left + tarjeta.width).toBeLessThanOrEqual(390 - 16);
  });
});

describe("los recorridos", () => {
  it("anclan solo a la navegación y no inventan cifras", () => {
    for (const r of [RECORRIDO_PROFESOR, RECORRIDO_ESTUDIANTE]) {
      for (const p of r.pasos) {
        if (p.ancla) expect(p.ancla).toMatch(/^nav:\//);
        expect(p.texto).not.toMatch(/\d/);
      }
    }
  });
});
