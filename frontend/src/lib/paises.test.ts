import { describe, expect, it } from "vitest";
import { banderaDe, FRECUENTES, paisConBandera, paises } from "./paises";

describe("los países de la lista", () => {
  it("la bandera sale del código", () => {
    expect(banderaDe("CO")).toBe("🇨🇴");
    expect(banderaDe("co")).toBe("");
  });

  it("se muestran con bandera y nombre en español", () => {
    expect(paisConBandera("co")).toBe("🇨🇴 Colombia");
    expect(paisConBandera(null)).toBe("");
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
