import { describe, expect, it } from "vitest";
import {
  contarPalabras,
  estadoBio,
  estadoTitular,
  MAX_PALABRAS_BIO,
  MIN_PALABRAS_BIO,
} from "@/lib/perfil-profesor";

const palabras = (n: number) => "palabra ".repeat(n).trim();

describe("contarPalabras", () => {
  it("ignora el espacio sobrante", () => {
    expect(contarPalabras("  hola   mundo  ")).toBe(2);
    expect(contarPalabras("   ")).toBe(0);
  });
});

describe("el titular avisa antes de guardar", () => {
  it("vacío informa del mínimo, sin marcarlo como error", () => {
    expect(estadoTitular("")).toMatchObject({ estado: "vacio" });
  });

  it("corto dice cuántas faltan", () => {
    expect(estadoTitular("Profesor de inglés")).toMatchObject({ estado: "corto" });
    expect(estadoTitular("Profesor de inglés").mensaje).toContain("2");
  });

  it("con cinco palabras ya está bien", () => {
    expect(estadoTitular(palabras(5))).toMatchObject({ estado: "ok" });
  });
});

describe("la descripción tiene mínimo y máximo", () => {
  it("por debajo del mínimo dice cuántas faltan", () => {
    const estado = estadoBio(palabras(MIN_PALABRAS_BIO - 3));
    expect(estado.estado).toBe("corto");
    expect(estado.mensaje).toContain("3");
  });

  it("dentro del rango cuenta sobre el máximo", () => {
    expect(estadoBio(palabras(40))).toMatchObject({ estado: "ok" });
    expect(estadoBio(palabras(40)).mensaje).toBe(`40 de ${MAX_PALABRAS_BIO} palabras`);
  });

  it("pasarse también se marca", () => {
    expect(estadoBio(palabras(MAX_PALABRAS_BIO + 1))).toMatchObject({ estado: "largo" });
  });

  it("los bordes exactos no son error", () => {
    expect(estadoBio(palabras(MIN_PALABRAS_BIO)).estado).toBe("ok");
    expect(estadoBio(palabras(MAX_PALABRAS_BIO)).estado).toBe("ok");
  });
});
