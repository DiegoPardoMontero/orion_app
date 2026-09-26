import { useQuery } from "@tanstack/react-query";
import type { DocumentoLegalData } from "@/components/DocumentoLegal";
import { apiFetch } from "@/lib/api/fetch";

/**
 * El acuerdo del profesor vigente, desde la base (antes era texto fijo aquí). Desde la 2.0 lleva el
 * mandato de recaudo: Orión recibe el dinero de las clases por cuenta del profe y se lo entrega.
 */
export function useAcuerdoDelProfesor(activo = true) {
  return useQuery({
    queryKey: ["legal", "TEACHER_AGREEMENT"],
    queryFn: () => apiFetch<DocumentoLegalData>("/api/v1/legal/TEACHER_AGREEMENT", { redirectOn401: false }),
    enabled: activo,
    staleTime: 10 * 60_000,
  });
}

/** Lo que la app le tiene que pedir aceptar a quien entra: Términos, política y, al profe, su acuerdo. */
export const PENDIENTES_KEY = ["me", "legal", "pending"] as const;

export function usePendientesLegales(activo: boolean) {
  return useQuery({
    queryKey: PENDIENTES_KEY,
    queryFn: () => apiFetch<{ documents: string[] }>("/api/v1/me/legal/pending"),
    enabled: activo,
    staleTime: 5 * 60_000,
  });
}
