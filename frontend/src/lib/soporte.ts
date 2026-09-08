import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

export type TicketStatus = "OPEN" | "ANSWERED" | "CLOSED";

export type TicketSummary = {
  code: string;
  category: string;
  categoryLabel: string;
  subject: string;
  status: TicketStatus;
  bookingId: string | null;
  /** Solo las categorías con plazo fijado por ley lo traen. Las demás, null. */
  dueAt: string | null;
  overdue: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TicketThread = {
  ticket: TicketSummary;
  messages: { authorId: string; mine: boolean; body: string; createdAt: string }[];
};

export type CategoryOption = { code: string; label: string; hasLegalDeadline: boolean };

/** Cómo se lee cada estado. «Respondida» y no «cerrada»: el hilo sigue abierto para responder. */
export const ETIQUETA_ESTADO: Record<TicketStatus, string> = {
  OPEN: "Esperando respuesta",
  ANSWERED: "Respondida",
  CLOSED: "Cerrada",
};

/** El tono del Badge que le corresponde a cada estado. */
export const TONO_ESTADO: Record<TicketStatus, "melocoton" | "menta" | "neutral"> = {
  OPEN: "melocoton",
  ANSWERED: "menta",
  CLOSED: "neutral",
};

const clave = {
  categorias: ["soporte", "categorias"] as const,
  mios: ["soporte", "mios"] as const,
  hilo: (code: string) => ["soporte", "hilo", code] as const,
  bandeja: ["soporte", "bandeja"] as const,
};

export function useCategoriasSoporte() {
  return useQuery({
    queryKey: clave.categorias,
    // El catálogo no cambia entre despliegues: pedirlo una vez por sesión sobra.
    staleTime: Infinity,
    queryFn: () => apiFetch<CategoryOption[]>("/api/v1/me/support/categories"),
  });
}

export function useMisSolicitudes() {
  return useQuery({
    queryKey: clave.mios,
    queryFn: () => apiFetch<TicketSummary[]>("/api/v1/me/support/tickets"),
  });
}

export function useHilo(code: string, esAdmin = false) {
  const base = esAdmin ? "/api/v1/admin/support" : "/api/v1/me/support";
  return useQuery({
    queryKey: clave.hilo(code),
    queryFn: () => apiFetch<TicketThread>(`${base}/tickets/${code}`),
  });
}

export function useAbrirSolicitud() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      category: string;
      subject: string;
      body: string;
      bookingId?: string;
    }) => apiFetch<TicketThread>("/api/v1/me/support/tickets", { method: "POST", body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: clave.mios }),
  });
}

export function useResponder(code: string, esAdmin = false) {
  const queryClient = useQueryClient();
  const base = esAdmin ? "/api/v1/admin/support" : "/api/v1/me/support";
  return useMutation({
    mutationFn: (body: string) =>
      apiFetch<TicketThread>(`${base}/tickets/${code}/replies`, {
        method: "POST",
        body: { body },
      }),
    onSuccess: (hilo) => {
      queryClient.setQueryData(clave.hilo(code), hilo);
      queryClient.invalidateQueries({ queryKey: clave.mios });
      queryClient.invalidateQueries({ queryKey: clave.bandeja });
    },
  });
}

export function useBandejaSoporte() {
  return useQuery({
    queryKey: clave.bandeja,
    queryFn: () => apiFetch<TicketSummary[]>("/api/v1/admin/support/tickets"),
  });
}

export function useCerrarSolicitud(code: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<TicketThread>(`/api/v1/admin/support/tickets/${code}/close`, { method: "POST" }),
    onSuccess: (hilo) => {
      queryClient.setQueryData(clave.hilo(code), hilo);
      queryClient.invalidateQueries({ queryKey: clave.bandeja });
    },
  });
}

/** Los datos de contacto publicados: el WhatsApp de soporte y su horario. Endpoint público. */
export type Contacto = {
  responsable: string;
  documento: string;
  domicilio: string;
  ciudad: string;
  correo: string;
  whatsapp: string;
  whatsappDigits: string;
  horario: string;
};

export function useContacto() {
  return useQuery({
    queryKey: ["legal", "contacto"],
    staleTime: Infinity,
    queryFn: () => apiFetch<Contacto>("/api/v1/legal/contacto"),
  });
}

/**
 * Cuántos días faltan para el vencimiento, en días de calendario y redondeando hacia abajo.
 * Negativo si ya venció. Se usa para pintar en rojo lo que se pasa de plazo.
 */
export function diasParaVencer(dueAt: string, ahora = new Date()): number {
  const MS_POR_DIA = 24 * 60 * 60 * 1000;
  return Math.floor((new Date(dueAt).getTime() - ahora.getTime()) / MS_POR_DIA);
}
