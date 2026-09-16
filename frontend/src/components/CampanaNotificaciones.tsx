"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck, Trash2, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
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
import { useCerrarConEscape } from "@/lib/useCerrarConEscape";

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
 * hay "marcar todas como leídas", borrar una, y vaciar las leídas. Un clic fuera cierra el panel.
 *
 * <p><strong>El panel se dibuja en un portal sobre {@code document.body}.</strong> Antes era un
 * {@code absolute} dentro del botón, y en varias pantallas se abría por detrás del contenido: un
 * {@code z-50} solo compite dentro de su propio contexto de apilamiento, así que bastaba con que un
 * ancestro tuviera z-index, transform u opacidad para dejarlo atrapado por muy alto que fuera el
 * número. Sacarlo del árbol lo arregla en todas las pantallas a la vez, incluidas las que todavía
 * no existen — que es lo que pedía el encargo.
 *
 * @param anclaje de qué lado del botón crece el panel. En la cabecera móvil crece hacia la
 * izquierda («derecha»: el borde derecho coincide con el del botón). En el lateral de escritorio
 * tiene que crecer hacia la derecha, sobre el contenido: el lateral mide 248 px y el panel 320,
 * así que anclado a la derecha se salía 130 px por el borde izquierdo de la pantalla y las
 * notificaciones aparecían cortadas.
 */
export function CampanaNotificaciones({
  anclaje = "derecha",
}: {
  anclaje?: "derecha" | "izquierda";
} = {}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [abierto, setAbierto] = useState(false);
  const boton = useRef<HTMLButtonElement>(null);
  const [caja, setCaja] = useState<{ top: number; left: number } | null>(null);

  // El portal vive fuera del árbol, así que la posición hay que calcularla: se mide el botón y se
  // coloca el panel debajo, sin salirse por ningún borde.
  const situar = useCallback(() => {
    const b = boton.current?.getBoundingClientRect();
    if (!b) return;
    const ancho = Math.min(320, window.innerWidth - 16);
    const left = anclaje === "derecha" ? b.right - ancho : b.left;
    setCaja({
      top: b.bottom + 8,
      left: Math.max(8, Math.min(left, window.innerWidth - ancho - 8)),
    });
  }, [anclaje]);

  useLayoutEffect(() => {
    if (abierto) situar();
  }, [abierto, situar]);

  useEffect(() => {
    if (!abierto) return;
    window.addEventListener("resize", situar);
    window.addEventListener("scroll", situar, true);
    return () => {
      window.removeEventListener("resize", situar);
      window.removeEventListener("scroll", situar, true);
    };
  }, [abierto, situar]);

  const noLeidas = useNotificacionesNoLeidas(true);
  useCerrarConEscape(abierto, () => setAbierto(false));
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

  const borrarUna = useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/me/notifications/${id}`, { method: "DELETE" }),
    onSuccess: refrescar,
  });

  const vaciarLeidas = useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/me/notifications/read", { method: "DELETE" }),
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
        ref={boton}
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

      {abierto && caja && createPortal(
        <>
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            onClick={() => setAbierto(false)}
            className="fixed inset-0 z-[90] cursor-default"
          />
          <div
            role="dialog"
            aria-label="Notificaciones"
            style={{ top: caja.top, left: caja.left }}
            className="fixed z-[100] w-[320px] max-w-[calc(100vw-1rem)] overflow-hidden rounded-card border border-border bg-surface-raised shadow-lg"
          >
            <div className="flex items-center justify-between border-b border-surface-sunken px-4 py-3">
              <p className="text-[14px] font-bold text-text">Notificaciones</p>
              <span className="flex items-center gap-3">
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
                {/* Solo las leídas: vaciar de un golpe algo que no se ha visto es perderlo. */}
                {data && data.some((n) => n.read) && (
                  <button
                    type="button"
                    onClick={() => vaciarLeidas.mutate()}
                    disabled={vaciarLeidas.isPending}
                    className="flex items-center gap-1 text-[12px] font-semibold text-text-muted transition-colors hover:text-text disabled:opacity-50"
                  >
                    <Trash2 size={14} strokeWidth={2} />
                    Vaciar leídas
                  </button>
                )}
              </span>
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
                  <li key={notif.id} className="group relative">
                    <button
                      type="button"
                      onClick={() => abrir(notif)}
                      className={`flex w-full items-start gap-2.5 border-b border-surface-sunken py-3 pl-4 pr-10 text-left transition-colors hover:bg-surface-sunken ${
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
                    <button
                      type="button"
                      aria-label={`Borrar la notificación «${notif.title}»`}
                      onClick={() => notif.id && borrarUna.mutate(notif.id)}
                      disabled={borrarUna.isPending}
                      className="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full text-text-muted opacity-0 transition-opacity hover:bg-surface-sunken hover:text-text focus-visible:opacity-100 focus-visible:shadow-focus group-hover:opacity-100 disabled:opacity-40"
                    >
                      <X size={15} strokeWidth={2.2} />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>,
        document.body,
      )}
    </div>
  );
}
