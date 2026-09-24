"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Flame, Sparkles, Trophy } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { Cargando, ErrorCarga } from "@/components/estados";
import type { FamiliaLogro } from "@/components/gamificacion/EstrellaLogro";
import { SelloDeLogro } from "@/components/gamificacion/SelloDeLogro";
import { Constelacion, formaDe } from "@/components/practica/Constelacion";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import { NOMBRE_FAMILIA, ORDEN_FAMILIAS, type Engagement, type Logro } from "@/lib/gamificacion";
import { diaCorto, estrellasDe, tituloDelSet, type SetDePractica } from "@/lib/practica";

/**
 * Mi cielo (handoff `design_handoff_orion_practica`, §10.9): arriba, las constelaciones que salieron
 * de practicar —una por set completo, con su forma y su halo si fue perfecta—; abajo, los logros por
 * familia, en pestañas, cada uno con su sello y lo que lo enciende.
 *
 * <p>Una familia a la vez y no todas apiladas: con seis familias el tablero era una lista de iconos
 * que había que recorrer entera para encontrar el que importa. La pestaña se puede pedir por la
 * dirección (`&familia=PRACTICA`), y así «Verlo en Mi cielo» abre justo la del logro nuevo.
 */
export function MiCielo() {
  const params = useSearchParams();
  const logros = useQuery({
    queryKey: ["me", "achievements"],
    queryFn: () => apiFetch<Logro[]>("/api/v1/me/achievements"),
  });
  const resumen = useQuery({
    queryKey: ["me", "engagement"],
    queryFn: () => apiFetch<Engagement>("/api/v1/me/engagement"),
  });
  const practicas = useQuery({
    queryKey: ["me", "practice", "history"],
    queryFn: () => apiFetch<SetDePractica[]>("/api/v1/me/practice/history"),
  });
  const pedida = params.get("familia") as FamiliaLogro | null;
  const [familia, setFamilia] = useState<FamiliaLogro | null>(pedida && ORDEN_FAMILIAS.includes(pedida) ? pedida : null);

  if (logros.isPending || resumen.isPending) return <Cargando filas={3} />;

  if (logros.isError || !logros.data || !resumen.data) {
    return <ErrorCarga mensaje="No pudimos cargar tu cielo." onReintentar={() => void logros.refetch()} />;
  }

  const todos = logros.data;
  const encendidos = todos.filter((l) => l.unlocked).length;
  const familias = ORDEN_FAMILIAS.filter((f) => todos.some((l) => l.family === f));
  const elegida = familia ?? familias[0];
  const deLaFamilia = todos.filter((l) => l.family === elegida);
  const completas = (practicas.data ?? []).filter((p) => p.status === "COMPLETED");

  return (
    <section className="practica @container">
      <h2 className="font-display text-[19px] font-bold">Tu cielo</h2>
      <p className="mt-1 text-[14px] text-text-secondary">
        {encendidos === 0
          ? "Todavía no has encendido ninguna. Aquí se quedan las que consigas."
          : `${encendidos} de ${todos.length} estrellas encendidas.`}
      </p>

      {/* Sin ninguna encendida, el cielo se queda —hay que ver lo que viene— pero encima va una
          invitación con la acción concreta. Es el estado que más gente va a ver, y tiene que
          empujar hacia la primera clase en vez de lamentar que no la haya. */}
      {encendidos === 0 && (
        <div className="anim-rise mt-4 flex flex-col items-center gap-3 rounded-card bg-surface-raised p-6 text-center shadow-sm sm:flex-row sm:text-left">
          <Rigel pose="animo" decorativo className="h-[110px] w-auto shrink-0" />
          <div className="min-w-0 flex-1">
            <h2 className="font-display text-[19px] font-bold">Tu cielo empieza vacío</h2>
            <p className="mt-1 text-[14px] leading-relaxed text-text-secondary">
              Dos se encienden con tu primera clase. Las demás llegan solas mientras practicas.
            </p>
            <Link
              href="/profesores"
              className="mt-3 inline-flex min-h-11 items-center gap-2 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
            >
              Buscar mi primer profesor
              <ArrowRight size={16} strokeWidth={2} />
            </Link>
          </div>
        </div>
      )}

      <div data-tour="mi-cielo" className="mt-4 flex flex-wrap gap-3 rounded-card">
        <Dato icono={<Sparkles size={15} strokeWidth={2} />} valor={resumen.data.points} etiqueta="puntos" />
        <Dato
          icono={<Flame size={15} strokeWidth={2} />}
          valor={resumen.data.currentStreakWeeks}
          etiqueta={resumen.data.currentStreakWeeks === 1 ? "semana seguida" : "semanas seguidas"}
        />
        <Dato icono={<Trophy size={15} strokeWidth={2} />} valor={resumen.data.bestStreakWeeks} etiqueta="tu mejor racha" />
      </div>

      {/* Las constelaciones: solo si ya hay alguna. Un panel vacío no enseña nada que el de
          «Práctica», con sus sellos por encender, no diga mejor. */}
      {completas.length > 0 && (
        <div className="mt-5 flex flex-col gap-3 rounded-tarjeta bg-noche p-[18px] text-crema @2xl:gap-3.5 @2xl:rounded-[28px] @2xl:px-7 @2xl:py-6">
          <div className="flex items-baseline justify-between">
            <strong className="text-[16px] @2xl:text-[18px]">Tus constelaciones</strong>
            <span className="text-[13px] text-[#E9DEF5] @2xl:text-[14px]">
              {completas.length} {completas.length === 1 ? "completa" : "completas"}
            </span>
          </div>
          <ul className="m-0 grid list-none grid-cols-2 gap-x-2.5 gap-y-3.5 p-0 @2xl:grid-cols-4 @2xl:gap-4">
            {completas.map((p) => (
              <li key={p.id}>
                <Link href={`/practica/${p.id}`} className="flex flex-col items-center gap-1 rounded-sub p-1 text-center @2xl:gap-1.5">
                  <Constelacion
                    estados={estrellasDe(p.items)}
                    forma={formaDe(p.id)}
                    ancho={200}
                    r={8}
                    lineas
                    perfecta={p.perfect}
                    fondo="noche"
                    etiqueta={`${tituloDelSet(p) ?? "Práctica"}: constelación ${p.perfect ? "perfecta" : "completa"}`}
                    className="w-[150px] @2xl:w-[200px]"
                  />
                  <span className="text-[12px] font-bold @2xl:text-[14px]">{tituloDelSet(p) ?? "Práctica"}</span>
                  {p.completedAt && <span className="text-[11px] text-[#E9DEF5] @2xl:text-[12px]">{diaCorto(p.completedAt)}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div role="tablist" aria-label="Familias de logros" className="mt-5 flex flex-wrap gap-2">
        {familias.map((f) => {
          const sel = f === elegida;
          return (
            <button
              key={f}
              type="button"
              role="tab"
              aria-selected={sel}
              aria-controls="logros-de-la-familia"
              onClick={() => setFamilia(f)}
              className={`inline-flex h-10 cursor-pointer items-center rounded-pill px-3.5 text-[14px] font-bold whitespace-nowrap @2xl:px-4 ${
                sel ? "bg-ink text-crema" : "border-[1.5px] border-line bg-white text-ink hover:bg-arena"
              }`}
            >
              {NOMBRE_FAMILIA[f]}
            </button>
          );
        })}
      </div>

      <ul
        id="logros-de-la-familia"
        role="tabpanel"
        aria-label={NOMBRE_FAMILIA[elegida]}
        className="m-0 mt-3 grid list-none grid-cols-2 gap-3 p-0 @xl:grid-cols-3 @4xl:grid-cols-6 @4xl:gap-3.5"
      >
        {deLaFamilia.map((l) => (
          <li
            key={l.code}
            className="flex flex-col items-center gap-2 rounded-tarjeta bg-white px-3 pt-4 pb-3.5 text-center @4xl:pt-[18px] @4xl:pb-4"
          >
            <SelloDeLogro logro={l} size={96} />
            <strong className="font-display text-[15px] leading-[1.15]">{l.name}</strong>
            <span className="text-[12px] leading-[1.4] text-ink-2">{l.description}</span>
            <span className={`text-[12px] font-extrabold ${l.unlocked ? "text-ok-icon" : "text-rigel-ink"}`}>
              {l.unlocked ? `+${l.points} · conseguido` : `+${l.points} puntos`}
            </span>
          </li>
        ))}
      </ul>

      <div className="mt-4 flex flex-wrap gap-2.5">
        <Link
          href="/logros/avatar"
          className="inline-flex min-h-11 items-center gap-2 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
        >
          <Sparkles size={16} strokeWidth={1.9} />
          Personalizar mi avatar
        </Link>
        <Link
          href="/cuenta"
          className="inline-flex min-h-11 items-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          Volver a mi perfil
        </Link>
      </div>
    </section>
  );
}

function Dato({
  icono,
  valor,
  etiqueta,
}: {
  icono: React.ReactNode;
  valor: number;
  etiqueta: string;
}) {
  return (
    <span className="inline-flex items-center gap-2 rounded-pill border border-border bg-surface-raised px-3.5 py-2">
      <span className="text-text-muted">{icono}</span>
      <span className="font-display text-[17px] font-bold tabular-nums">{valor}</span>
      <span className="text-[12.5px] text-text-secondary">{etiqueta}</span>
    </span>
  );
}
