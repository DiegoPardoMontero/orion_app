import { describe, expect, it } from "vitest";
import { momentosDeAsistencia } from "./asistencia";

// Una clase de 5:00 a 5:55 PM en Bogotá.
const INICIO = "2026-09-27T17:00:00-05:00";
const FIN = "2026-09-27T17:55:00-05:00";
const en = (hora: string) => Date.parse(`2026-09-27T${hora}:00-05:00`);

describe("momentosDeAsistencia", () => {
  it("«asistió» se abre al terminar la clase, no antes", () => {
    expect(momentosDeAsistencia(INICIO, FIN, 15).asistio).toBe(en("17:55"));
  });

  it("«no se presentó» se abre al inicio más la espera sembrada", () => {
    expect(momentosDeAsistencia(INICIO, FIN, 15).noAsistio).toBe(en("17:15"));
  });

  it("la espera sale de Ajustes: con 30 minutos, a las 5:30", () => {
    expect(momentosDeAsistencia(INICIO, FIN, 30).noAsistio).toBe(en("17:30"));
  });

  it("no depende de la zona del navegador: el ISO trae su offset", () => {
    const utc = momentosDeAsistencia("2026-09-27T22:00:00Z", "2026-09-27T22:55:00Z", 15);
    expect(utc.asistio).toBe(en("17:55"));
    expect(utc.noAsistio).toBe(en("17:15"));
  });
});
