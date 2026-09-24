import { describe, expect, it } from "vitest";
import { RECORRIDO_ESTUDIANTE, RECORRIDO_PROFESOR, ubicarTarjeta } from "./recorrido";

const escritorio = { width: 1280, height: 800 };
const movil = { width: 390, height: 844 };

describe("ubicarTarjeta", () => {
  it("abajo del foco cuando cabe, alineada con él y con la flecha apuntándole", () => {
    const r = ubicarTarjeta({ top: 100, left: 600, width: 200, height: 40 }, 220, escritorio, false);
    expect(r).toMatchObject({ top: 156, left: 520, width: 360, lugar: "abajo" });
    // La flecha (16 px) queda centrada bajo el foco: 700 − 520 − 8.
    expect(r.flecha).toBe(172);
  });

  it("al lado de un ítem de la barra lateral, como en la captura 11", () => {
    const r = ubicarTarjeta({ top: 110, left: 7, width: 234, height: 66 }, 290, escritorio, false);
    expect(r.lugar).toBe("derecha");
    expect(r.left).toBe(7 + 234 + 16);
  });

  it("en móvil va a lo ancho, 20 px por lado, y sobre la barra inferior", () => {
    const r = ubicarTarjeta({ top: 763, left: 0, width: 100, height: 81 }, 238, movil, true);
    expect(r).toMatchObject({ left: 20, width: 350, lugar: "arriba" });
    expect(r.top).toBe(763 - 16 - 238);
    // La flecha nunca se sale de la esquina redondeada de la tarjeta.
    expect(r.flecha).toBeGreaterThanOrEqual(22);
  });

  it("en móvil nunca al lado: si no cabe arriba ni abajo, centrada", () => {
    const r = ubicarTarjeta({ top: 200, left: 20, width: 350, height: 500 }, 240, movil, true);
    expect(r.lugar).toBe("centro");
  });

  it("un botón a la derecha de la pantalla deja la tarjeta abajo y dentro, como en la captura 12", () => {
    const r = ubicarTarjeta({ top: 147, left: 1029, width: 184, height: 70 }, 238, escritorio, false);
    expect(r.lugar).toBe("abajo");
    expect(r.left + r.width).toBeLessThanOrEqual(1280 - 16);
  });

  it("centrada si no hay foco", () => {
    expect(ubicarTarjeta(null, 200, escritorio, false)).toMatchObject({ top: 300, left: 460, lugar: "centro", flecha: null });
  });
});

describe("los recorridos", () => {
  it("tienen los pasos del diseño: 8 del profe y 6 del estudiante, Meissa solo en la práctica", () => {
    expect(RECORRIDO_PROFESOR.pasos).toHaveLength(8);
    expect(RECORRIDO_ESTUDIANTE.pasos).toHaveLength(6);
    expect(RECORRIDO_PROFESOR.pasos.findIndex((p) => p.guia === "meissa")).toBe(5);
    expect(RECORRIDO_ESTUDIANTE.pasos.findIndex((p) => p.guia === "meissa")).toBe(3);
    expect(RECORRIDO_PROFESOR.pasos.filter((p) => p.guia === "meissa")).toHaveLength(1);
  });

  it("cada paso lleva a una pantalla y termina en un ancla que existe siempre: la navegación", () => {
    for (const r of [RECORRIDO_PROFESOR, RECORRIDO_ESTUDIANTE]) {
      for (const p of r.pasos) {
        expect(p.ruta).not.toBeNull();
        expect(p.anclas.at(-1)).toMatch(/^nav:\//);
      }
    }
  });

  it("el cierre lleva el nombre del profe", () => {
    expect(RECORRIDO_PROFESOR.cierre.titulo("Mariana")).toBe("¡Listo, Mariana! Ya conoces Orión.");
  });
});
