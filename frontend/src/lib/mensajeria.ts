"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";
import type {
  ConversationSummary,
  NotificationResponse,
  UnreadCountResponse,
} from "@/lib/api/types";

/**
 * Estado compartido de la mensajería interna (Bloque 3). La bandeja y el contador de no leídos
 * viven bajo una sola clave de caché: la lista de conversaciones ya trae `unreadCount` por hilo,
 * así que el badge de la navegación se deriva de ella sin una consulta aparte.
 */

export const conversacionesKey = ["conversations"] as const;
export const notificacionesKey = ["notifications"] as const;
export const notifNoLeidasKey = ["notifications", "unread-count"] as const;

/** La bandeja del estudiante o del profesor. `refetchInterval` la mantiene fresca sin recargar. */
export function useConversaciones(habilitado = true) {
  return useQuery({
    queryKey: conversacionesKey,
    queryFn: () => apiFetch<ConversationSummary[]>("/api/v1/conversations"),
    enabled: habilitado,
    refetchInterval: habilitado ? 30_000 : false,
  });
}

/** Total de mensajes sin leer sumando los hilos: alimenta el badge de "Mensajes" en la nav. */
export function useMensajesNoLeidos(habilitado = true): number {
  const { data } = useConversaciones(habilitado);
  return (data ?? []).reduce((total, conv) => total + (conv.unreadCount ?? 0), 0);
}

/** Las notificaciones in-app del usuario autenticado. Se piden al abrir la campana. */
export function useNotificaciones(habilitado: boolean) {
  return useQuery({
    queryKey: notificacionesKey,
    queryFn: () => apiFetch<NotificationResponse[]>("/api/v1/me/notifications"),
    enabled: habilitado,
  });
}

/** Contador de notificaciones sin leer para el badge de la campana (poll cada 30 s). */
export function useNotificacionesNoLeidas(habilitado = true): number {
  const { data } = useQuery({
    queryKey: notifNoLeidasKey,
    queryFn: () => apiFetch<UnreadCountResponse>("/api/v1/me/notifications/unread-count"),
    enabled: habilitado,
    refetchInterval: habilitado ? 30_000 : false,
  });
  return data?.count ?? 0;
}

/**
 * El backend guarda `linkPath` tal cual; una notificación antigua puede apuntar a `/postulacion`,
 * ruta que en el frontend v2 es `/aplicacion/estado`. Solo mapeamos ese caso; lo demás va directo.
 */
export function rutaNotificacion(linkPath: string | undefined): string {
  if (!linkPath) return "/mensajes";
  if (linkPath === "/postulacion" || linkPath.startsWith("/postulacion/")) {
    return "/aplicacion/estado";
  }
  return linkPath;
}
