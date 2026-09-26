import { describe, expect, it } from "vitest";
import { estadoParaElProfe, type LiquidacionDelProfe } from "@/lib/liquidaciones";

const base: LiquidacionDelProfe = {
  id: "x",
  periodStart: "2026-10-01",
  periodEnd: "2026-10-15",
  status: "DRAFT",
  statusLabel: "Borrador",
  holdReason: null,
  netCop: 40000,
  committedPayDate: "2026-10-20",
  paidOn: null,
};

describe("estadoParaElProfe", () => {
  it("una pagada dice el día", () => {
    expect(estadoParaElProfe({ ...base, status: "PAID", paidOn: "2026-10-16" }).texto).toBe("Pagada el viernes, 16 de octubre");
  });

  it("en revisión y aprobada prometen la fecha comprometida", () => {
    expect(estadoParaElProfe(base).texto).toBe("En revisión: te la pagamos a más tardar el martes, 20 de octubre");
    expect(estadoParaElProfe({ ...base, status: "APPROVED" }).texto).toBe(
      "Aprobada: te la transferimos a más tardar el martes, 20 de octubre",
    );
  });

  it("retenida dice por qué", () => {
    const r = estadoParaElProfe({ ...base, status: "ON_HOLD", holdReason: "Faltan los datos de pago" });
    expect(r.texto).toBe("Retenida: faltan los datos de pago");
    expect(r.tono).toBe("melocoton");
  });

  it("la que se arrastra lo dice", () => {
    expect(estadoParaElProfe({ ...base, status: "CARRIED_OVER" }).texto).toBe("Sin pago: el saldo pasa a tu siguiente liquidación");
  });
});
