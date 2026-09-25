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
  it("la comisión y el beneficio de fundador salen de Ajustes", () => {
    const r = preguntas(cifras).profesor.find((q) => q.p === "¿Cuánto retiene Orión?")!.r;
    expect(r).toContain("El 20 % del precio de la clase");
    expect(r).toContain("15 % durante sus primeros 3 meses de clases");
  });

  it("el plazo para pagar sale de payment_hold_minutes", () => {
    const r = preguntas(cifras).estudiante.find((q) => q.p.startsWith("Reservé y no me confirmó"))!.r;
    expect(r).toContain("Tienes 35 minutos para pagarlo");
    expect(r).not.toContain("20 minutos");
  });
});
