/** Cómo se le cuenta al usuario cada estado del backend. */
const ETIQUETAS: Record<string, string> = {
  PENDING_PAYMENT: "Pendiente de pago",
  EXPIRED: "Vencida sin pagar",
  CONFIRMED: "Confirmada",
  CANCELLED_BY_STUDENT: "Cancelada por el estudiante",
  CANCELLED_BY_PROFESSOR: "Cancelada por el profesor",
  CANCELLED_BY_ADMIN: "Cancelada por Orión",
  COMPLETED: "Completada",
  UNDER_REVIEW: "En revisión",
  NO_SHOW_STUDENT: "El estudiante no llegó",
  NO_SHOW_PROFESSOR: "El profesor no llegó",
};

/**
 * La inasistencia se cuenta según quién lee: al que faltó se le habla de tú, y al admin, en tercera
 * persona. Antes el profe cuyo estudiante no llegó leía «No asististe».
 */
export function etiquetaEstado(estado?: string, lector?: "estudiante" | "profesor"): string {
  if (estado === "NO_SHOW_STUDENT" && lector === "estudiante") return "No asististe";
  if (estado === "NO_SHOW_PROFESSOR" && lector === "profesor") return "No asististe";
  return (estado && ETIQUETAS[estado]) ?? estado ?? "";
}

export function esCancelada(estado?: string): boolean {
  return !!estado && estado.startsWith("CANCELLED");
}

/** El cupo está apartado pero la clase todavía no existe: falta que entre el pago. */
export function esperaPago(estado?: string): boolean {
  return estado === "PENDING_PAYMENT";
}
