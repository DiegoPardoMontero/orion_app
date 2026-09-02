/** Cómo se le cuenta al usuario cada estado del backend. */
const ETIQUETAS: Record<string, string> = {
  PENDING_PAYMENT: "Pendiente de pago",
  EXPIRED: "Vencida sin pagar",
  CONFIRMED: "Confirmada",
  CANCELLED_BY_STUDENT: "Cancelada por el estudiante",
  CANCELLED_BY_PROFESSOR: "Cancelada por el profesor",
  CANCELLED_BY_ADMIN: "Cancelada por Orión",
  COMPLETED: "Completada",
  NO_SHOW: "No asistió",
};

export function etiquetaEstado(estado?: string): string {
  return (estado && ETIQUETAS[estado]) ?? estado ?? "";
}

export function esCancelada(estado?: string): boolean {
  return !!estado && estado.startsWith("CANCELLED");
}

/** El cupo está apartado pero la clase todavía no existe: falta que entre el pago. */
export function esperaPago(estado?: string): boolean {
  return estado === "PENDING_PAYMENT";
}
