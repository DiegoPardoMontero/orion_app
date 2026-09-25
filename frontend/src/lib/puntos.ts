"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

/**
 * Los puntos del estudiante (24/09/2026): «un score que no significa nada más que hacer puntos».
 * No compran nada ni ordenan a nadie; enseñan cuánto ha recorrido alguien en Orión, y por eso van
 * junto a su nombre en todas partes. Solo los hacen los estudiantes.
 */

/** Lo que devuelve `/api/v1/me/points`. */
export type MisPuntos = {
  total: number;
  recent: MovimientoDePuntos[];
  ways: ManeraDePuntos[];
};

export type MovimientoDePuntos = {
  source: string;
  points: number;
  occurredAt: string;
  /** El nombre del logro, o el del profe de la clase, la reseña o la conversación. */
  detail: string | null;
};

export type ManeraDePuntos = { source: string; points: number; once: boolean };

export const misPuntosKey = ["me", "points"] as const;

/** El total. Una clave para todas las pantallas: los chips no se contradicen. */
export function useMisPuntos(habilitado = true) {
  return useQuery({
    queryKey: misPuntosKey,
    queryFn: () => apiFetch<MisPuntos>("/api/v1/me/points"),
    enabled: habilitado,
    staleTime: 30_000,
  });
}

/** «1.240»: con separador de miles, como se escriben las cifras en Colombia. */
export function cifraDePuntos(n: number): string {
  return new Intl.NumberFormat("es-CO").format(n);
}
