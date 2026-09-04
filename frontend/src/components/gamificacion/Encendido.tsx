"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { EstrellaLogro } from "@/components/gamificacion/EstrellaLogro";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import type { Logro } from "@/lib/gamificacion";

/**
 * El encendido: la celebración de §2f.
 *
 * <p>Los 720 ms son el único derroche permitido del sistema, y por eso está acotado: cuatro cuadros
 * en CSS, sin confeti ni sonido, y <strong>nunca encadenado</strong> — si se encienden dos estrellas
 * a la vez se muestran en secuencia con 400 ms entre ellas, porque dos celebraciones simultáneas no
 * son el doble de fiesta, son ruido.
 *
 * <p>Lo que dispara la celebración es la diferencia entre lo que el servidor dice que está encendido
 * y lo último que esta persona ya vio, guardado en el navegador. La primera visita <em>no</em>
 * celebra nada: se anota el estado en silencio. Si no fuera así, a quien el backfill le encendió
 * ocho estrellas le caerían ocho celebraciones seguidas la primera vez que abre la app.
 */
export function Encendido() {
  const me = useMe();
  const esEstudiante = me.data?.role === "STUDENT";

  const logros = useQuery({
    queryKey: ["me", "achievements"],
    queryFn: () => apiFetch<Logro[]>("/api/v1/me/achievements"),
    enabled: esEstudiante,
    staleTime: 30_000,
  });

  const [cola, setCola] = useState<Logro[]>([]);
  const cerrando = useRef(false);
  const clave = me.data ? `orion.encendidas.${me.data.id}` : null;

  useEffect(() => {
    if (!clave || !logros.data) return;

    const encendidas = logros.data.filter((l) => l.unlocked);
    const codigos = encendidas.map((l) => l.code);
    const guardado = leer(clave);
    escribir(clave, codigos);

    // Primera vez en este navegador: se anota y ya. Nada que celebrar hacia atrás.
    if (guardado === null) return;

    const nuevas = encendidas.filter((l) => !guardado.includes(l.code));
    // La regla pide no llamar a setState dentro de un efecto, y con razón en el caso habitual:
    // casi siempre significa que el dato era derivable en el render. Aquí no lo es — depende de
    // `localStorage`, que no se puede leer durante el render sin romper el renderizado en el
    // servidor, y la comparación tiene que ocurrir una sola vez por respuesta, no en cada pintado.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (nuevas.length > 0) setCola((previas) => [...previas, ...nuevas]);
  }, [clave, logros.data]);

  const actual = cola[0];

  useEffect(() => {
    if (!actual) return;
    cerrando.current = false;
    // 720 ms de animación + 400 ms de respiro antes de la siguiente.
    const t = setTimeout(() => setCola((c) => c.slice(1)), 1120);
    return () => clearTimeout(t);
  }, [actual]);

  useEffect(() => {
    if (!actual) return;
    const alTeclado = (e: KeyboardEvent) => {
      if (e.key === "Escape") setCola([]);
    };
    window.addEventListener("keydown", alTeclado);
    return () => window.removeEventListener("keydown", alTeclado);
  }, [actual]);

  if (!actual) return null;

  return (
    <div
      className="fixed inset-0 z-[80] grid place-items-center bg-[rgba(26,26,46,0.55)] px-6"
      role="status"
      aria-live="polite"
      onClick={() => setCola([])}
    >
      <div className="flex flex-col items-center text-center">
        <div className="relative grid place-items-center">
          {/* El anillo crema que se expande y se disuelve (cuadro 3). */}
          <span
            aria-hidden="true"
            className="encendido-anillo pointer-events-none absolute h-[168px] w-[168px] rounded-full border-2 border-surface"
          />
          {/* Los tres destellos del cuadro 4, con 60 ms de desfase entre ellos. */}
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              aria-hidden="true"
              className="encendido-destello pointer-events-none absolute h-2 w-2 rounded-full bg-surface"
              style={{
                animationDelay: `${480 + i * 60}ms`,
                transform: `rotate(${i * 120}deg) translateY(-96px)`,
              }}
            />
          ))}
          <div className="encendido-estrella">
            <EstrellaLogro
              familia={actual.family}
              brillo={actual.glow}
              estado="encendida"
              size={148}
              sobreCielo
              titulo={actual.name}
            />
          </div>
        </div>

        <p className="mt-5 text-[12px] font-bold uppercase tracking-[0.14em] text-surface/75">
          Estrella encendida
        </p>
        <h2 className="mt-1 font-display text-h2 font-bold text-surface">{actual.name}</h2>
        <p className="mt-1 max-w-[34ch] text-[14px] leading-relaxed text-surface/80">
          {actual.description}
        </p>

        {/* Rigel solo en la versión completa: con movimiento reducido no aparece. */}
        <Rigel pose="celebracion" decorativo className="encendido-rigel mt-4 h-20 w-auto" />

        {cola.length > 1 && (
          <p className="mt-3 text-[12px] text-surface/65">
            Y {cola.length - 1} más en camino
          </p>
        )}
      </div>
    </div>
  );
}

function leer(clave: string): string[] | null {
  try {
    const crudo = window.localStorage.getItem(clave);
    return crudo === null ? null : (JSON.parse(crudo) as string[]);
  } catch {
    // Un navegador sin almacenamiento no debe romper la app: simplemente nunca celebra.
    return [];
  }
}

function escribir(clave: string, codigos: string[]) {
  try {
    window.localStorage.setItem(clave, JSON.stringify(codigos));
  } catch {
    /* sin almacenamiento no hay nada que anotar */
  }
}
