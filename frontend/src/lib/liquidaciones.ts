import { fechaLarga } from "@/lib/format";

/** Estados de una liquidación, como los manda el backend (PayoutStatus). */
export type EstadoLiquidacion = "DRAFT" | "APPROVED" | "PAID" | "ON_HOLD" | "CARRIED_OVER";

export type LiquidacionDelProfe = {
  id: string;
  periodStart: string;
  periodEnd: string;
  status: EstadoLiquidacion;
  statusLabel: string;
  holdReason: string | null;
  netCop: number;
  committedPayDate: string;
  paidOn: string | null;
};

const dia = (fecha: string) => fechaLarga(`${fecha}T12:00:00-05:00`);

/**
 * Lo que el profe lee de cada liquidación en «Mis ganancias»: qué pasa con ella y cuándo le llega,
 * sin la palabra técnica. «Retenida» dice por qué y qué puede hacer él.
 */
export function estadoParaElProfe(l: LiquidacionDelProfe): { texto: string; tono: "menta" | "lavanda" | "melocoton" | "neutral" } {
  switch (l.status) {
    case "PAID":
      return { texto: l.paidOn ? `Pagada el ${dia(l.paidOn)}` : "Pagada", tono: "menta" };
    case "APPROVED":
      return { texto: `Aprobada: te la transferimos a más tardar el ${dia(l.committedPayDate)}`, tono: "lavanda" };
    case "DRAFT":
      return { texto: `En revisión: te la pagamos a más tardar el ${dia(l.committedPayDate)}`, tono: "lavanda" };
    case "ON_HOLD":
      return { texto: `Retenida: ${(l.holdReason ?? "falta un dato").toLowerCase()}`, tono: "melocoton" };
    case "CARRIED_OVER":
      return { texto: "Sin pago: el saldo pasa a tu siguiente liquidación", tono: "neutral" };
  }
}
