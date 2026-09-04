"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Flame, Sparkles, Trophy } from "lucide-react";
import Link from "next/link";
import { Cargando, ErrorCarga } from "@/components/estados";
import { EstrellaLogro } from "@/components/gamificacion/EstrellaLogro";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import {
  estadoDe,
  numeralDe,
  NOMBRE_FAMILIA,
  ORDEN_FAMILIAS,
  type Engagement,
  type Logro,
} from "@/lib/gamificacion";

/**
 * El cielo: las cinco constelaciones con sus estrellas.
 *
 * <p>Se apila por familia y no en una cuadrícula uniforme porque cada familia cuenta una historia
 * distinta —la constancia es una escalera, el volumen otra— y mezclarlas convierte el tablero en
 * una lista de iconos. El fondo es el amanecer cortado antes del durazno, para que lo encendido
 * contraste.
 */
export default function LogrosPage() {
  const logros = useQuery({
    queryKey: ["me", "achievements"],
    queryFn: () => apiFetch<Logro[]>("/api/v1/me/achievements"),
  });

  const resumen = useQuery({
    queryKey: ["me", "engagement"],
    queryFn: () => apiFetch<Engagement>("/api/v1/me/engagement"),
  });

  if (logros.isPending || resumen.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-4xl lg:px-12 lg:py-8">
        <Cargando filas={3} />
      </main>
    );
  }

  if (logros.isError || !logros.data || !resumen.data) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-4xl lg:px-12">
        <ErrorCarga mensaje="No pudimos cargar tu cielo." onReintentar={() => void logros.refetch()} />
      </main>
    );
  }

  const todos = logros.data;
  const encendidos = todos.filter((l) => l.unlocked).length;
  // La próxima que se puede encender: la de más avance entre las que faltan. Es lo que Rigel señala.
  const proxima = todos
    .filter((l) => !l.unlocked)
    .sort((a, b) => b.progress / b.target - a.progress / a.target)[0];

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-4xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Tu cielo</h1>
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

      <div className="mt-4 flex flex-wrap gap-3">
        <Dato icono={<Sparkles size={15} strokeWidth={2} />} valor={resumen.data.points} etiqueta="puntos" />
        <Dato
          icono={<Flame size={15} strokeWidth={2} />}
          valor={resumen.data.currentStreakWeeks}
          etiqueta={resumen.data.currentStreakWeeks === 1 ? "semana seguida" : "semanas seguidas"}
        />
        <Dato icono={<Trophy size={15} strokeWidth={2} />} valor={resumen.data.bestStreakWeeks} etiqueta="tu mejor racha" />
      </div>

      {/* El cielo. El degradado es el mismo amanecer del login, cortado antes del durazno. */}
      <div
        className="relative mt-5 overflow-hidden rounded-card px-5 py-6 lg:px-8 lg:py-8"
        style={{ background: "var(--gradient-sky)" }}
      >
        {/* Rigel aparece UNA sola vez en todo el tablero, señalando la próxima. No en móvil, y no
            cuando ya está todo encendido: ahí no hay nada que señalar. */}
        {proxima && (
          <div className="pointer-events-none absolute right-4 top-4 hidden items-center gap-2 lg:flex">
            <p className="max-w-[16ch] text-right text-[12.5px] font-semibold leading-tight text-on-primary/80">
              La siguiente: {proxima.name}
            </p>
            <Rigel pose="guia" decorativo className="h-[90px] w-auto" />
          </div>
        )}

        <div className="flex flex-col gap-7">
          {ORDEN_FAMILIAS.map((familia) => {
            const deLaFamilia = todos.filter((l) => l.family === familia);
            if (deLaFamilia.length === 0) return null;
            const completa = deLaFamilia.every((l) => l.unlocked);

            return (
              <section key={familia}>
                <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-on-primary/70">
                  {NOMBRE_FAMILIA[familia]}
                  {completa && <span className="ml-2 text-on-primary">· completa</span>}
                </p>
                <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-4">
                  {deLaFamilia.map((logro) => (
                    <li key={logro.code} className="w-[86px] text-center">
                      <EstrellaLogro
                        familia={logro.family}
                        brillo={logro.glow}
                        estado={estadoDe(logro)}
                        progreso={{ hecho: logro.progress, total: logro.target }}
                        numeral={numeralDe(logro.code)}
                        size={72}
                        sobreCielo
                        titulo={`${logro.name}. ${logro.description}`}
                      />
                      <p className="mt-1.5 text-[11.5px] font-semibold leading-tight text-on-primary">
                        {logro.name}
                      </p>
                      {/* Se nombra lo que la persona hizo. Nunca «te faltan», nunca una ausencia. */}
                      <p className="mt-0.5 text-[10.5px] leading-tight text-on-primary/65">
                        {logro.unlocked
                          ? "Encendida"
                          : logro.target > 1
                            ? `${logro.progress} de ${logro.target}`
                            : logro.description}
                      </p>
                    </li>
                  ))}
                </ul>
              </section>
            );
          })}
        </div>
      </div>

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
    </main>
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
