import { describe, expect, it } from "vitest";
import { rutaDelBuscador } from "./buscador";

describe("rutaDelBuscador", () => {
  it("sin elegir nada lleva al directorio entero", () => {
    expect(rutaDelBuscador(null, null)).toBe("/profesores");
  });

  it("«Trabajo» son los objetivos de negocios y de entrevistas", () => {
    expect(rutaDelBuscador("trabajo", null)).toBe("/profesores?goal=BUSINESS&goal=INTERVIEW");
  });

  it("una franja va como schedule, y el fin de semana como sábado y domingo", () => {
    expect(rutaDelBuscador("viaje", "noche")).toBe("/profesores?goal=TRAVEL&schedule=EVENING");
    expect(rutaDelBuscador(null, "finde")).toBe("/profesores?day=SATURDAY&day=SUNDAY");
  });
});
