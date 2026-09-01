import { describe, expect, it } from "vitest";
import { diaBogota, horaBogota, iniciales } from "@/lib/format";

describe("iniciales", () => {
  it("toma las dos primeras iniciales", () => {
    expect(iniciales("María Gómez")).toBe("MG");
  });

  it("con un solo nombre da una inicial", () => {
    expect(iniciales("Ana")).toBe("A");
  });

  it("ignora espacios extra", () => {
    expect(iniciales("  Juan   Torres  ")).toBe("JT");
  });
});

describe("hora y día en Bogotá (independiente de la zona del navegador)", () => {
  it("un instante con offset -05:00 se lee tal cual en Bogotá", () => {
    expect(horaBogota("2026-07-15T10:00:00-05:00")).toBe("10:00");
    expect(diaBogota("2026-07-15T10:00:00-05:00")).toBe("2026-07-15");
  });

  it("un instante en UTC se convierte a la hora de Bogotá", () => {
    // 15:00Z = 10:00 en Bogotá (UTC-5).
    expect(horaBogota("2026-07-15T15:00:00Z")).toBe("10:00");
  });
});
