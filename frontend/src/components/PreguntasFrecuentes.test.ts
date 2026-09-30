import { describe, expect, it } from "vitest";
import type { PublicFigures } from "@/lib/api/types";
import { preguntas } from "./PreguntasFrecuentes";

const cifras: PublicFigures = {
  commissionPercent: 20,
  classMinutes: 55,
  paymentHoldMinutes: 35,
  studentCancelHours: 12,
  professorCancelHours: 12,
  bookingMinLeadHours: 6,
  noShowReportMinutes: 15,
  disputeReportWindowHours: 24,
  autoCompleteHours: 24,
  applicationReviewBusinessDays: 3,
  assessmentMinutes: 2,
  assessmentLeadRetentionDays: 30,
  founderCommissionPercent: 15,
  founderPeriodMonths: 3,
};

describe("las preguntas frecuentes dicen las reglas de Ajustes, no una copia", () => {
  // Pardo, 29/09/2026: la comisión y el descuento de fundador se ven al poner la tarifa, no antes.
  it("ninguna respuesta dice la cifra de la comisión ni la del fundador", () => {
    const r = preguntas(cifras).profesor.find((q) => q.p === "¿Cuánto retiene Orión?")!.r;
    expect(r).toContain("al fijar tu tarifa");
    const todas = Object.values(preguntas(cifras)).flat().map((q) => q.r).join(" ");
    expect(todas).not.toMatch(/\b(20|15) ?%/);
    expect(preguntas(cifras).portadaProfesor.map((q) => q.p)).not.toContain("¿Cuánto cobra Orión de comisión?");
  });

  it("el plazo para pagar sale de payment_hold_minutes", () => {
    const r = preguntas(cifras).estudiante.find((q) => q.p.startsWith("Reservé y mi clase no sale confirmada"))!.r;
    expect(r).toContain("Tienes 35 minutos para pagar;");
    expect(r).not.toContain("20 minutos");
  });
});
