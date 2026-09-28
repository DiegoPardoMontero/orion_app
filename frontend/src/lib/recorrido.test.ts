import { describe, expect, it } from "vitest";
import { DESLIZ_MINIMO, deslizHastaSuSitio, RECORRIDO_ESTUDIANTE, RECORRIDO_PROFESOR, ubicarTarjeta } from "./recorrido";

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

  it("las actas y las clases dictadas se buscan en «Pasadas», que es donde se pintan", () => {
    // En «Próximas» la lista de actas no existe: el paso esperaba 1,8 s y caía en la navegación.
    for (const r of [RECORRIDO_PROFESOR, RECORRIDO_ESTUDIANTE]) {
      for (const p of r.pasos.filter((p) => p.anclas[0] === "actas" || p.anclas[0] === "clase-pasada")) {
        expect(p.ruta).toBe("/mis-clases?scope=past");
      }
    }
  });

  it("el cierre lleva el nombre del profe", () => {
    expect(RECORRIDO_PROFESOR.cierre.titulo("Mariana")).toBe("¡Listo, Mariana! Ya conoces Orión.");
  });
});

describe("deslizHastaSuSitio", () => {
  const escritorioVista = { alto: 800, arriba: 0, abajo: 0, scroll: 0, scrollMax: 1200 };
  const movilVista = { alto: 844, arriba: 64, abajo: 68, scroll: 0, scrollMax: 1500 };

  it("lleva lo que está lejos hasta un tercio del alto", () => {
    expect(deslizHastaSuSitio({ top: 1400, height: 60 }, escritorioVista)).toBe(1400 - 267);
  });

  it("la grilla de horarios, más alta que la pantalla y arriba, igual se mueve (antes: píldora sin desliz)", () => {
    // La medida real a 1280 × 800: arriba en 217, 830 de alto y 279 de recorrido.
    const d = deslizHastaSuSitio({ top: 217, height: 830 }, { ...escritorioVista, scrollMax: 279 });
    expect(d).toBe(DESLIZ_MINIMO);
  });

  it("si ya está en su sitio, un desliz corto que lo deja a la vista", () => {
    const d = deslizHastaSuSitio({ top: 270, height: 44 }, escritorioVista);
    expect(Math.abs(d ?? 0)).toBeGreaterThanOrEqual(DESLIZ_MINIMO);
    expect(270 - (d ?? 0)).toBeGreaterThanOrEqual(16);
  });

  it("al final de la página, el desliz corto va hacia arriba", () => {
    const d = deslizHastaSuSitio({ top: 300, height: 44 }, { ...escritorioVista, scroll: 1200 });
    expect(d).toBe(-DESLIZ_MINIMO);
  });

  it("en el celular no mete el elemento bajo la cabecera", () => {
    // Pegado a la cabecera y con la página arriba del todo: no hay hacia dónde ir sin taparlo.
    expect(deslizHastaSuSitio({ top: 90, height: 40 }, movilVista)).toBeNull();
    // Más abajo sí: sube y queda debajo de ella.
    const d = deslizHastaSuSitio({ top: 250, height: 40 }, movilVista) ?? 0;
    expect(d).toBeGreaterThanOrEqual(DESLIZ_MINIMO);
    expect(250 - d).toBeGreaterThanOrEqual(64 + 16);
  });

  it("una página que no se desplaza no se mueve: sin desliz, sin píldora", () => {
    expect(deslizHastaSuSitio({ top: 400, height: 60 }, { ...escritorioVista, scrollMax: 0 })).toBeNull();
  });
});
