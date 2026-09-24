import { describe, expect, it } from "vitest";
import { faltanDeLaFicha } from "./ficha";

describe("lo que le falta a la ficha", () => {
  const completa = {
    photoUrl: "https://img/ana.png",
    selfDeclaredLevel: "INTERMEDIATE" as const,
    primaryLanguage: "EN",
    goalCodes: ["TRAVEL"],
    motivation: "Viajar sin miedo.",
  };

  it("completa no le falta nada", () => {
    expect(faltanDeLaFicha(completa)).toEqual([]);
  });

  it("nombra en palabras lo que falta, en el orden de la ficha", () => {
    expect(faltanDeLaFicha({ ...completa, photoUrl: null, goalCodes: [], motivation: "   " })).toEqual([
      "tu foto",
      "para qué lo aprendes",
      "tu motivación",
    ]);
  });
});
