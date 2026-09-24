"use client";

import { useQuery } from "@tanstack/react-query";
import { useState, useSyncExternalStore } from "react";
import { apiFetch } from "@/lib/api/fetch";
import { esNavegadorDeApp } from "@/lib/navegador";
import { DESDE_KEY, INTENCION_KEY } from "@/lib/auth/roles";

type Proveedor = "google" | "microsoft" | "apple" | "facebook";

/**
 * «Continuar con Google / Microsoft / Apple / Facebook». Solo aparecen los proveedores configurados en este
 * despliegue (regla de Pardo: nada de interfaz muerta), así que sin ninguno el componente no pinta
 * nada, ni siquiera el separador.
 *
 * <p>Cada botón respeta el aspecto que pide su marca —Google en blanco con su «G», Apple en negro,
 * Facebook en su azul—, porque un botón de proveedor que no se parece al del proveedor es un botón
 * en el que la gente no confía.
 *
 * <p>Antes de salir deja en sessionStorage de dónde venía la persona (`desde`), para que la vuelta
 * —que llega a otra URL— la lleve al mismo sitio que el login con contraseña.
 */
export function BotonesSociales({
  desde,
  separador = "o con tu correo",
  ensenar = false,
}: {
  desde?: string;
  separador?: string;
  /** Viene de «Quiero enseñar»: la cuenta nace como aspirante a profesor, no como estudiante. */
  ensenar?: boolean;
}) {
  const { data } = useQuery({
    queryKey: ["auth", "social", "providers"],
    queryFn: () =>
      apiFetch<{ providers: Proveedor[] }>("/api/v1/auth/social/providers", { redirectOn401: false }),
    staleTime: 10 * 60_000,
    retry: false,
  });
  const proveedores = data?.providers ?? [];
  // El que se tocó, mientras el navegador se va: un segundo toque abría otra solicitud y pisaba la
  // primera, y la persona volvía a «No pudimos entrar».
  const [yendo, setYendo] = useState<Proveedor | null>(null);
  const dentroDeUnaApp = useSyncExternalStore(
    () => () => {},
    () => esNavegadorDeApp(navigator.userAgent),
    () => false,
  );
  if (proveedores.length === 0) return null;

  const ir = (p: Proveedor) => {
    if (yendo) return;
    setYendo(p);
    const origen = desde ?? new URLSearchParams(window.location.search).get("desde");
    try {
      if (origen) window.sessionStorage.setItem(DESDE_KEY, origen);
      if (ensenar) window.sessionStorage.setItem(INTENCION_KEY, "ensenar");
      else window.sessionStorage.removeItem(INTENCION_KEY);
    } catch {
      // Sin almacenamiento: al volver entra al inicio de su rol. No es motivo para no dejarle entrar.
    }
    window.location.assign(`/oauth2/authorization/${p}`);
  };

  return (
    <div>
      {/* Instagram, TikTok y Facebook abren los enlaces en su propio navegador, y Google no deja
          entrar ahí (lo rechaza en su pantalla, sin que Orión se entere). Se avisa antes de tocar. */}
      {dentroDeUnaApp && (
        <div role="note" className="mb-3 rounded-base bg-warning-bg px-4 py-3 text-[13px] leading-relaxed text-warning">
          <strong>Estás dentro de una app</strong> (Instagram, TikTok…) y ahí Google no deja entrar. Abre
          esta página en Chrome o Safari —menú <strong>⋯</strong> → «Abrir en el navegador»— o entra con tu
          correo aquí abajo.
        </div>
      )}
      <div className="grid gap-2.5">
        {ORDEN.filter((p) => proveedores.includes(p)).map((p) => (
          <button
            key={p}
            type="button"
            onClick={() => ir(p)}
            disabled={yendo !== null}
            aria-busy={yendo === p || undefined}
            className={`${ESTILO[p]} disabled:cursor-default disabled:opacity-70`}
          >
            {LOGO[p]}
            {yendo === p ? `Abriendo ${NOMBRE[p]}…` : `Continuar con ${NOMBRE[p]}`}
          </button>
        ))}
      </div>
      {separador && (
        <p className="my-4 flex items-center gap-3 text-[12.5px] text-text-muted">
          <span className="h-px flex-1 bg-border" />
          {separador}
          <span className="h-px flex-1 bg-border" />
        </p>
      )}
    </div>
  );
}

const ORDEN: Proveedor[] = ["google", "microsoft", "apple", "facebook"];

const NOMBRE: Record<Proveedor, string> = {
  google: "Google",
  microsoft: "Microsoft",
  apple: "Apple",
  facebook: "Facebook",
};

const BASE =
  "inline-flex h-12 w-full items-center justify-center gap-2.5 rounded-pill px-5 text-[15px] font-semibold transition-colors focus-visible:shadow-focus";

const ESTILO: Record<Proveedor, string> = {
  google: `${BASE} border border-[#DADCE0] bg-white text-[#1F1F1F] hover:bg-[#F8F9FA]`,
  // El botón claro de Microsoft: fondo blanco, borde gris y su logo de cuatro cuadros.
  microsoft: `${BASE} border border-[#8C8C8C] bg-white text-[#5E5E5E] hover:bg-[#F3F3F3]`,
  apple: `${BASE} bg-black text-white hover:bg-[#1a1a1a]`,
  facebook: `${BASE} bg-[#1877F2] text-white hover:bg-[#166FE5]`,
};

const LOGO: Record<Proveedor, React.ReactNode> = {
  google: (
    <svg viewBox="0 0 48 48" width={18} height={18} aria-hidden="true">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  ),
  microsoft: (
    <svg viewBox="0 0 21 21" width={18} height={18} aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#F25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
      <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
      <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
    </svg>
  ),
  apple: (
    <svg viewBox="0 0 24 24" width={18} height={18} aria-hidden="true" fill="currentColor">
      <path d="M16.37 12.62c-.02-2.2 1.8-3.26 1.88-3.31-1.03-1.5-2.62-1.7-3.19-1.73-1.36-.14-2.65.8-3.34.8-.69 0-1.75-.78-2.88-.76-1.48.02-2.85.86-3.61 2.19-1.54 2.67-.39 6.62 1.11 8.79.73 1.06 1.6 2.25 2.74 2.21 1.1-.04 1.51-.71 2.84-.71 1.33 0 1.7.71 2.86.69 1.18-.02 1.93-1.08 2.65-2.15.84-1.23 1.18-2.42 1.2-2.48-.03-.01-2.3-.88-2.32-3.49zM14.18 6.16c.6-.73 1.01-1.75.9-2.76-.87.04-1.92.58-2.54 1.31-.56.64-1.05 1.67-.92 2.66.97.08 1.96-.49 2.56-1.21z" />
    </svg>
  ),
  facebook: (
    <svg viewBox="0 0 24 24" width={18} height={18} aria-hidden="true" fill="currentColor">
      <path d="M24 12.07C24 5.4 18.63 0 12 0S0 5.4 0 12.07C0 18.1 4.39 23.1 10.13 24v-8.44H7.08v-3.49h3.05V9.41c0-3.02 1.79-4.7 4.53-4.7 1.31 0 2.68.24 2.68.24v2.97h-1.51c-1.49 0-1.95.93-1.95 1.88v2.26h3.33l-.53 3.49h-2.8V24C19.61 23.1 24 18.1 24 12.07z" />
    </svg>
  ),
};
