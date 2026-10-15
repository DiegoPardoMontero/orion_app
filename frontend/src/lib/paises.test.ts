import { describe, expect, it } from "vitest";
import { codigoDeBandera, FRECUENTES, nombreDePais, paises } from "./paises";

describe("los países de la lista", () => {
  it("el código sale de un código o de un emoji de bandera", () => {
    expect(codigoDeBandera("CO")).toBe("CO");
    expect(codigoDeBandera("co")).toBe("CO");
    expect(codigoDeBandera("🇨🇴")).toBe("CO");
    expect(codigoDeBandera("🇬🇧")).toBe("GB");
    expect(codigoDeBandera("✦")).toBe("");
    expect(codigoDeBandera(null)).toBe("");
  });

  it("el nombre va en español", () => {
    expect(nombreDePais("CO")).toBe("Colombia");
  });

  it("Colombia va primero y después vienen todos, sin repetir", () => {
    const lista = paises();
    expect(lista[0].code).toBe("CO");
    expect(new Set(lista.map((p) => p.code)).size).toBe(lista.length);
    expect(lista.length).toBeGreaterThan(200);
    const resto = lista.slice(FRECUENTES).map((p) => p.nombre);
    expect(resto).toEqual([...resto].sort((a, b) => a.localeCompare(b, "es")));
  });
});
