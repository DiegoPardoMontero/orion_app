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

/** El total y lo último. Una clave para todas las pantallas: el chip y la tarjeta no se contradicen. */
export function useMisPuntos(habilitado = true) {
  return useQuery({
    queryKey: misPuntosKey,
    queryFn: () => apiFetch<MisPuntos>("/api/v1/me/points"),
    enabled: habilitado,
    staleTime: 30_000,
  });
}

/** Cómo se dice cada manera de hacer puntos, en la lista de «Cómo se hacen». */
const MANERA: Record<string, string> = {
  LESSON: "Cada clase que tomas",
  PUNCTUAL: "Entrar al aula a tiempo",
  REVIEW: "Calificar tu clase",
  PRACTICE: "Terminar una práctica",
  PRACTICE_PERFECT: "Práctica perfecta, sin fallar",
  MESSAGE: "Escribirle a un profe por primera vez",
  PROFILE_PUBLIC: "Hacer visible tu ficha",
  DIAGNOSTIC: "Tu diagnóstico con Meissa",
  TOUR: "Terminar el recorrido de bienvenida",
};

export function nombreDeLaManera(source: string): string {
  return MANERA[source] ?? source;
}

/** Una línea del historial: «Clase con María», «Logro · Primera reserva». */
export function queDioPuntos(m: Pick<MovimientoDePuntos, "source" | "detail">): string {
  const con = m.detail ? ` con ${m.detail}` : "";
  switch (m.source) {
    case "LESSON":
      return `Clase${con}`;
    case "PUNCTUAL":
      return `Llegaste a tiempo${m.detail ? ` a la clase con ${m.detail}` : ""}`;
    case "REVIEW":
      return `Calificaste tu clase${con}`;
    case "PRACTICE":
      return "Terminaste una práctica";
    case "PRACTICE_PERFECT":
      return "Práctica perfecta";
    case "MESSAGE":
      return m.detail ? `Primer mensaje a ${m.detail}` : "Primer mensaje a un profe";
    case "PROFILE_PUBLIC":
      return "Hiciste visible tu ficha";
    case "DIAGNOSTIC":
      return "Tu diagnóstico con Meissa";
    case "TOUR":
      return "Terminaste el recorrido";
    case "ACHIEVEMENT":
      return m.detail ? `Logro · ${m.detail}` : "Un logro nuevo";
    default:
      return "Puntos";
  }
}

/** «1.240»: con separador de miles, como se escriben las cifras en Colombia. */
export function cifraDePuntos(n: number): string {
  return new Intl.NumberFormat("es-CO").format(n);
}
