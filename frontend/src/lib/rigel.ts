import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";
import type { RigelThreadResponse } from "@/lib/api/types";

/**
 * El hilo de Rigel: los mensajes oficiales de Orión en «Mensajes» (24/09/2026). Leerlo trae la
 * bienvenida si faltaba, así que basta con pedirlo para que aparezca.
 */
export const rigelKey = ["me", "rigel"] as const;

export function useRigel(habilitado = true) {
  return useQuery({
    queryKey: rigelKey,
    queryFn: () => apiFetch<RigelThreadResponse>("/api/v1/me/rigel"),
    enabled: habilitado,
    refetchInterval: habilitado ? 60_000 : false,
  });
}
