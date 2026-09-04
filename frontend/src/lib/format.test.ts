import { describe, expect, it } from "vitest";
import {
  diaBogota,
  hora12,
  hora12Compacta,
  horaBogota,
  iniciales,
  minutoDelDiaBogota,
  rangoCompacto,
  rangoHoras,
} from "@/lib/format";

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
    expect(horaBogota("2026-07-15T10:00:00-05:00")).toBe("10:00 AM");
    expect(diaBogota("2026-07-15T10:00:00-05:00")).toBe("2026-07-15");
  });

  it("un instante en UTC se convierte a la hora de Bogotá", () => {
    // 15:00Z = 10:00 en Bogotá (UTC-5).
    expect(horaBogota("2026-07-15T15:00:00Z")).toBe("10:00 AM");
  });
});

describe("la hora se lee en formato de 12 horas", () => {
  it("distingue la mañana de la tarde", () => {
    expect(horaBogota("2026-07-15T09:00:00-05:00")).toBe("9:00 AM");
    expect(horaBogota("2026-07-15T18:30:00-05:00")).toBe("6:30 PM");
  });

  it("medianoche es 12 AM y mediodía es 12 PM, no 0 ni 24", () => {
    expect(horaBogota("2026-07-15T00:00:00-05:00")).toBe("12:00 AM");
    expect(horaBogota("2026-07-15T12:00:00-05:00")).toBe("12:00 PM");
  });

  it("un rango dentro del mismo meridiano solo lo dice una vez", () => {
    expect(rangoHoras("2026-07-15T09:00:00-05:00", "2026-07-15T10:00:00-05:00")).toBe(
      "9:00 – 10:00 AM",
    );
  });

  it("y lo repite cuando el rango cruza el mediodía", () => {
    expect(rangoHoras("2026-07-15T11:00:00-05:00", "2026-07-15T12:00:00-05:00")).toBe(
      "11:00 AM – 12:00 PM",
    );
  });

  it("una hora de pared se convierte sin fecha de por medio", () => {
    expect(hora12("18:00")).toBe("6:00 PM");
    expect(hora12("00:30")).toBe("12:30 AM");
    expect(hora12Compacta("18:00")).toBe("6 PM");
    expect(hora12Compacta("18:30")).toBe("6:30 PM");
  });
});

describe("rangoCompacto (horas de pared, para la grilla semanal)", () => {
  it("dice el meridiano una sola vez si el rango no cruza el mediodía", () => {
    expect(rangoCompacto("18:00", "21:00")).toBe("6–9 PM");
    expect(rangoCompacto("08:00", "11:00")).toBe("8–11 AM");
  });

  it("lo dice en ambos extremos cuando sí lo cruza", () => {
    expect(rangoCompacto("11:00", "13:00")).toBe("11 AM–1 PM");
  });

  it("conserva los minutos cuando no son en punto", () => {
    expect(rangoCompacto("18:30", "20:00")).toBe("6:30–8 PM");
  });
});

describe("minutoDelDiaBogota", () => {
  it("ordena las horas por el momento y no por su etiqueta", () => {
    // El caso que rompía la grilla semanal: como texto, «10:00 AM» < «9:00 AM» y «7:00 PM» < «8:00 AM».
    const nueve = "2026-09-09T09:00:00-05:00";
    const diez = "2026-09-09T10:00:00-05:00";
    const siete = "2026-09-07T19:00:00-05:00";

    expect(minutoDelDiaBogota(nueve)).toBeLessThan(minutoDelDiaBogota(diez));
    expect(minutoDelDiaBogota(diez)).toBeLessThan(minutoDelDiaBogota(siete));
  });

  it("lee el minuto en Bogotá aunque el instante venga en UTC", () => {
    // 23:00 UTC son las 18:00 en Bogotá.
    expect(minutoDelDiaBogota("2026-09-09T23:00:00Z")).toBe(18 * 60);
  });

  it("cuenta la medianoche como cero y no como 1440", () => {
    expect(minutoDelDiaBogota("2026-09-09T00:00:00-05:00")).toBe(0);
  });
});
