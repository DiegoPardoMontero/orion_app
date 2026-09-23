import { describe, expect, it } from "vitest";
import { leerPayload, palabrasNuevas, primerNombre, resumirLoTrabajado, type Ejercicio } from "./practica";

describe("la invitación a practicar", () => {
  it("resume lo trabajado en una línea, sin punto final y en minúscula para ir tras los dos puntos", () => {
    expect(resumirLoTrabajado("Trabajamos past simple; sigue diciendo 'I go yesterday'.")).toBe("trabajamos past simple");
    expect(resumirLoTrabajado("Condicionales.")).toBe("condicionales");
    expect(resumirLoTrabajado("   ")).toBeNull();
    expect(resumirLoTrabajado(null)).toBeNull();
  });

  it("corta lo trabajado demasiado largo con puntos suspensivos", () => {
    const largo = "Trabajamos " + "muchas cosas distintas ".repeat(10);
    const r = resumirLoTrabajado(largo)!;
    expect(r.length).toBeLessThanOrEqual(68);
    expect(r.endsWith("…")).toBe(true);
  });

  it("dice las palabras nuevas en letras, como se lee en voz alta", () => {
    expect(palabrasNuevas(1)).toBe("una palabra nueva");
    expect(palabrasNuevas(5)).toBe("cinco palabras nuevas");
    expect(palabrasNuevas(20)).toBe("20 palabras nuevas");
  });

  it("nombra al profesor por su nombre de pila", () => {
    expect(primerNombre("  María  Gómez ")).toBe("María");
  });
});

describe("leerPayload", () => {
  it("un payload ilegible no rompe la pantalla: vuelve vacío", () => {
    const roto = { payload: "{no es json" } as Ejercicio;
    expect(leerPayload<{ term?: string }>(roto)).toEqual({});
  });
});
