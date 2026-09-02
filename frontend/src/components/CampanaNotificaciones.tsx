"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/fetch";
import type { NotificationResponse } from "@/lib/api/types";
import { fechaCorta, horaBogota } from "@/lib/format";
import {
  notifNoLeidasKey,
  notificacionesKey,
  rutaNotificacion,
  useNotificaciones,
  useNotificacionesNoLeidas,
} from "@/lib/mensajeria";

/** Momento relativo compacto para el panel: hora si es de hoy, si no la fecha corta. */
function cuando(iso: string | undefined): string {
  if (!iso) return "";
  const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(new Date());
  const dia = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(new Date(iso));
  return dia === hoy ? horaBogota(iso) : fechaCorta(iso);
}

/**
 * La campana del shell: un botón con badge del número de notificaciones sin leer (poll cada 30 s) y
 * un panel desplegable. Al tocar una notificación se marca leída y se navega a su `linkPath`; también
 * hay "marcar todas como leídas". Un clic fuera cierra el panel.
 */
export function CampanaNotificaciones() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [abierto, setAbierto] = useState(false);

  const noLeidas = useNotificacionesNoLeidas(true);
  const { data, isPending, isError, refetch } = useNotificaciones(abierto);

  function refrescar() {
    void queryClient.invalidateQueries({ queryKey: notificacionesKey });
    void queryClient.invalidateQueries({ queryKey: notifNoLeidasKey });
  }

  const marcarUna = useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/me/notifications/${id}/read`, { method: "POST" }),
    onSuccess: refrescar,
  });

  const marcarTodas = useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/me/notifications/read-all", { method: "POST" }),
    onSuccess: refrescar,
  });

  function abrir(notif: NotificationResponse) {
    if (!notif.read && notif.id) {
      marcarUna.mutate(notif.id);
    }
    setAbierto(false);
    router.push(rutaNotificacion(notif.linkPath));
  }

  return (
    <div className="relative">
      <button
        type="button"
        aria-label={
          noLeidas > 0 ? `Notificaciones, ${noLeidas} sin leer` : "Notificaciones"
        }
        onClick={() => setAbierto((v) => !v)}
        className="relative grid h-11 w-11 place-items-center rounded-full text-text-secondary transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
      >
        <Bell size={20} strokeWidth={1.75} fill={abierto ? "currentColor" : "none"} />
        {noLeidas > 0 && (
          <span className="absolute right-1.5 top-1.5 grid h-[17px] min-w-[17px] place-items-center rounded-pill bg-primary px-1 text-[10px] font-bold text-on-primary">
            {noLeidas > 9 ? "9+" : noLeidas}
          </span>
        )}
      </button>

      {abierto && (
        <>
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            onClick={() => setAbierto(false)}
            className="fixed inset-0 z-40 cursor-default"
          />
          <div className="absolute right-0 top-full z-50 mt-2 w-[320px] max-w-[calc(100vw-2rem)] overflow-hidden rounded-card border border-border bg-surface-raised shadow-lg">
            <div className="flex items-center justify-between border-b border-surface-sunken px-4 py-3">
              <p className="text-[14px] font-bold text-text">Notificaciones</p>
              {noLeidas > 0 && (
                <button
                  type="button"
                  onClick={() => marcarTodas.mutate()}
                  disabled={marcarTodas.isPending}
                  className="flex items-center gap-1 text-[12px] font-semibold text-primary-strong transition-colors hover:text-primary disabled:opacity-50"
                >
                  <CheckCheck size={14} strokeWidth={2} />
                  Marcar todas
                </button>
              )}
            </div>

            <div className="max-h-[60vh] overflow-y-auto">
              {isPending && (
                <p className="px-4 py-6 text-center text-[13px] text-text-muted">Cargando…</p>
              )}
              {isError && (
                <div className="px-4 py-6 text-center">
                  <p className="text-[13px] text-text-secondary">No pudimos cargar las notificaciones.</p>
                  <button
                    type="button"
                    onClick={() => void refetch()}
                    className="mt-2 text-[13px] font-semibold text-primary-strong"
                  >
                    Reintentar
                  </button>
                </div>
              )}
              {data && data.length === 0 && (
                <p className="px-4 py-8 text-center text-[13px] text-text-muted">
                  No tienes notificaciones por ahora.
                </p>
              )}

              <ul>
                {data?.map((notif) => (
                  <li key={notif.id}>
                    <button
                      type="button"
                      onClick={() => abrir(notif)}
                      className={`flex w-full items-start gap-2.5 border-b border-surface-sunken px-4 py-3 text-left transition-colors hover:bg-surface-sunken ${
                        notif.read ? "" : "bg-primary-soft/40"
                      }`}
                    >
                      <span
                        aria-hidden="true"
                        className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                          notif.read ? "bg-transparent" : "bg-primary"
                        }`}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-baseline justify-between gap-2">
                          <span className="truncate text-[13px] font-bold text-text">
                            {notif.title}
                          </span>
                          <span className="shrink-0 text-[10.5px] text-text-muted">
                            {cuando(notif.createdAt)}
                          </span>
                        </span>
                        {notif.body && (
                          <span className="mt-0.5 block text-[12.5px] leading-snug text-text-secondary">
                            {notif.body}
                          </span>
                        )}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
