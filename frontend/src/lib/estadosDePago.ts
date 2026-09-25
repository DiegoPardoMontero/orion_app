/**
 * Cómo se le cuenta a cada quien el estado de un pago. Tres tablas y no una porque el mismo estado
 * no significa lo mismo para los tres: un pago liberado es, para el estudiante, una clase dictada; para
 * el profesor, dinero por cobrar; para el admin, dinero que ya no está retenido.
 *
 * Viven juntas para que ninguna se quede atrás cuando el backend estrena un estado: la de Saldo ya
 * enseñó una vez «REFUND_PENDING» tal cual, y la de Ganancias volvió a hacerlo. La prueba de al lado
 * exige que las tres cubran todos.
 */

type Tono = "menta" | "melocoton" | "lavanda" | "neutral" | "error";
export type Estado = { texto: string; tono: Tono };

/** Los estados de `billing.domain.PaymentStatus`, en su orden. */
export const ESTADOS_DE_PAGO = [
  "PENDING",
  "PAID",
  "RELEASED",
  "REFUND_PENDING",
  "REFUNDED",
  "DISPUTED",
  "CANCELLED",
] as const;

/** Para el estudiante, en «Pagos y saldo». Nunca aparece la comisión. */
export const PARA_EL_ESTUDIANTE: Record<string, Estado> = {
  PENDING: { texto: "Pendiente de pago", tono: "melocoton" },
  PAID: { texto: "Pagada", tono: "menta" },
  RELEASED: { texto: "Clase dictada", tono: "menta" },
  REFUND_PENDING: { texto: "Devolución en camino", tono: "melocoton" },
  REFUNDED: { texto: "Devuelta a tu saldo", tono: "neutral" },
  DISPUTED: { texto: "En revisión", tono: "melocoton" },
  CANCELLED: { texto: "No se completó", tono: "error" },
};

/**
 * Para el profesor, en «Ganancias». Además de los del pago, dos de la liquidación: un pago liberado
 * que ya va en una sale como «En camino» o «Transferido», igual que las cifras de arriba.
 */
export const PARA_EL_PROFESOR: Record<string, Estado> = {
  PENDING: { texto: "Sin pagar aún", tono: "neutral" },
  PAID: { texto: "Retenido", tono: "melocoton" },
  RELEASED: { texto: "Por cobrar", tono: "menta" },
  IN_TRANSIT: { texto: "En camino", tono: "lavanda" },
  TRANSFERRED: { texto: "Transferido", tono: "lavanda" },
  REFUND_PENDING: { texto: "Devolución al estudiante en curso", tono: "neutral" },
  REFUNDED: { texto: "Devuelto al estudiante", tono: "neutral" },
  DISPUTED: { texto: "En revisión", tono: "melocoton" },
  CANCELLED: { texto: "Cancelado", tono: "error" },
};

/** Para el admin, en «Pagos»: las mismas palabras que sus filtros. */
export const PARA_EL_ADMIN: Record<string, Estado> = {
  PENDING: { texto: "Pendiente", tono: "neutral" },
  PAID: { texto: "Retenido", tono: "melocoton" },
  RELEASED: { texto: "Liberado", tono: "menta" },
  REFUND_PENDING: { texto: "Devolución pendiente", tono: "melocoton" },
  REFUNDED: { texto: "Devuelto", tono: "lavanda" },
  DISPUTED: { texto: "En revisión", tono: "melocoton" },
  CANCELLED: { texto: "Cancelado", tono: "error" },
};

/** El estado en palabras; uno desconocido sale tal cual antes que desaparecer. */
export function estadoDePago(tabla: Record<string, Estado>, status: string): Estado {
  return tabla[status] ?? { texto: status, tono: "neutral" };
}
