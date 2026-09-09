import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

export type Elegibilidad = {
  eligible: boolean;
  reason: string | null;
  /** Hasta cuándo se podría ejercer. Nulo cuando ya no aplica. */
  deadline: string | null;
};

export type Devolucion = {
  id: string;
  bookingId: string;
  studentId: string;
  studentName: string;
  reason: "RETRACTO" | "ADMIN";
  amountCop: number;
  status: "PENDING" | "PAID";
  dueAt: string;
  daysLeft: number;
  overdue: boolean;
  requestedAt: string;
  paidAt: string | null;
  wompiReference: string | null;
};

/**
 * Si esta clase admite retracto. Se pregunta por clase y solo cuando está confirmada y por venir:
 * preguntarlo para todas las tarjetas de la lista serían N peticiones para enseñar un botón que
 * casi nunca aplica.
 */
export function useElegibilidadRetracto(bookingId: string, activo: boolean) {
  return useQuery({
    queryKey: ["retracto", bookingId],
    enabled: activo,
    queryFn: () =>
      apiFetch<Elegibilidad>(`/api/v1/me/bookings/${bookingId}/retraction`),
  });
}

export function useRetractarse(bookingId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<Devolucion>(`/api/v1/me/bookings/${bookingId}/retraction`, { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      queryClient.invalidateQueries({ queryKey: ["slots"] });
      queryClient.invalidateQueries({ queryKey: ["retracto", bookingId] });
    },
  });
}

export function useDevolucionesPendientes() {
  return useQuery({
    queryKey: ["admin", "refunds"],
    queryFn: () => apiFetch<Devolucion[]>("/api/v1/admin/refunds"),
  });
}

export function useConfirmarDevolucion(refundId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { reference: string; note?: string }) =>
      apiFetch<Devolucion>(`/api/v1/admin/refunds/${refundId}/confirm`, {
        method: "POST",
        body: input,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "refunds"] }),
  });
}
