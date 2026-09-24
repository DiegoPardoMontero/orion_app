"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { SelloDeLogro } from "@/components/gamificacion/SelloDeLogro";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import { NOMBRE_FAMILIA, type Logro } from "@/lib/gamificacion";

/**
 * El logro nuevo (handoff `design_handoff_orion_practica`, §10.6): un momento reutilizable en toda
 * la app. El sello entra estampándose, con su nombre, lo que lo encendió y sus puntos; y se sigue con
 * un botón, porque un logro que se va solo en un segundo es un logro que no se alcanzó a leer.
 *
 * <p><strong>Nunca encadenado</strong>: si se encienden dos a la vez van en cola, uno tras otro —dos
 * celebraciones simultáneas no son el doble de fiesta, son ruido—.
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
  const seguir = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!actual) return;
    seguir.current?.focus();
    const alTeclado = (e: KeyboardEvent) => {
      if (e.key === "Escape") setCola((c) => c.slice(1));
    };
    window.addEventListener("keydown", alTeclado);
    return () => window.removeEventListener("keydown", alTeclado);
  }, [actual]);

  if (!actual) return null;

  const siguiente = () => setCola((c) => c.slice(1));
  const ceja = `Logro nuevo · ${NOMBRE_FAMILIA[actual.family]}`;

  return (
    <div className="practica pr-aparece fixed inset-0 z-[80] flex items-center justify-center bg-[rgba(46,30,78,.86)] p-5">
      <div
        key={actual.code}
        role="dialog"
        aria-modal="true"
        aria-labelledby="logro-nuevo"
        aria-describedby="logro-nuevo-por"
        className="flex w-full max-w-[440px] flex-col items-center gap-3 rounded-[28px] bg-white px-[22px] pt-7 pb-[18px] text-center text-ink lg:grid lg:w-[640px] lg:max-w-none lg:grid-cols-[220px_minmax(0,1fr)] lg:items-center lg:gap-7 lg:px-10 lg:pt-9 lg:pb-6 lg:text-left"
      >
        <span className="text-[12px] font-extrabold tracking-[.12em] text-durazno-ink uppercase lg:hidden">{ceja}</span>
        <div className="flex flex-col items-center">
          <span className="lg:hidden">
            <SelloDeLogro logro={actual} size={170} estampar />
          </span>
          <span className="hidden lg:block">
            <SelloDeLogro logro={actual} size={200} estampar />
          </span>
        </div>
        <div className="flex flex-col items-center gap-3 lg:items-start">
          <span className="hidden text-[12px] font-extrabold tracking-[.12em] text-durazno-ink uppercase lg:block">{ceja}</span>
          <h2 id="logro-nuevo" className="m-0 font-display text-[28px] leading-[1.1] font-extrabold lg:text-[34px] lg:leading-[1.05]">
            {actual.name}
          </h2>
          <p id="logro-nuevo-por" className="m-0 text-[15px] leading-[1.5] text-ink-2 lg:text-[16px]">
            {actual.description}
          </p>
          <span className="inline-flex h-9 items-center rounded-pill bg-rigel-soft px-4 text-[15px] font-extrabold text-rigel-ink">
            +{actual.points} puntos
          </span>
          {/* Rigel muestra el sello solo en el celular; en escritorio el sello ya ocupa su lado. */}
          <Rigel pose="sello" decorativo className="h-auto w-24 lg:hidden" />
          <div className="flex flex-col gap-1.5 self-stretch lg:flex-row lg:gap-2.5 lg:self-auto lg:pt-1.5">
            <button
              ref={seguir}
              type="button"
              onClick={siguiente}
              className="h-14 cursor-pointer rounded-pill bg-coral px-8 text-[16px] font-bold text-crema transition-colors hover:bg-coral-hover lg:h-[52px]"
            >
              Seguir
            </button>
            <Link
              href={`/cuenta?seccion=cielo&familia=${actual.family}`}
              onClick={() => setCola([])}
              className="flex h-12 items-center justify-center rounded-pill px-[18px] text-[15px] font-bold text-ink hover:bg-arena lg:h-[52px]"
            >
              Verlo en Mi cielo
            </Link>
          </div>
          {cola.length > 1 && <p className="m-0 text-[12px] text-ink-3">Y {cola.length - 1} más en camino</p>}
        </div>
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
