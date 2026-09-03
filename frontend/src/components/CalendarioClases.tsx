"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";
import type { MyBookingResponse } from "@/lib/api/types";
import { diaBogota, horaBogota } from "@/lib/format";

const DIAS_CORTOS = ["L", "M", "X", "J", "V", "S", "D"];

const MESES = [
  "enero", "febrero", "marzo", "abril", "mayo", "junio",
  "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
];

/** "2026-07-15" → {anio: 2026, mes: 6, dia: 15}. El mes va en base 0, como en Date. */
function partes(iso: string) {
  const [anio, mes, dia] = iso.split("-").map(Number);
  return { anio, mes: mes - 1, dia };
}

function claveDia(anio: number, mes: number, dia: number): string {
  return `${anio}-${String(mes + 1).padStart(2, "0")}-${String(dia).padStart(2, "0")}`;
}

/**
 * El mes que se muestra al abrir: el de la primera clase de la lista, no el de hoy. En "pasadas"
 * eso evita abrir en un mes vacío cuando la última clase fue en marzo, y en "próximas" coincide
 * con el mes en curso salvo que no quede ninguna, que es justo cuando interesa saltar al siguiente.
 */
function mesInicial(clases: MyBookingResponse[]): { anio: number; mes: number } {
  const primera = clases.find((clase) => clase.startsAt);
  const hoy = new Date();
  if (!primera) return { anio: hoy.getFullYear(), mes: hoy.getMonth() };
  const { anio, mes } = partes(diaBogota(primera.startsAt!));
  return { anio, mes };
}

/**
 * Las clases del mes en una rejilla. Complementa a la agenda, no la sustituye: la lista contesta
 * "qué tengo ahora" y el calendario "cómo va mi mes" —dónde se acumulan las clases y qué semanas
 * están vacías—, que es una pregunta de forma y no de orden.
 *
 * La semana empieza en lunes, como se lee un calendario en Colombia.
 */
export function CalendarioClases({
  clases,
  onElegirDia,
}: {
  clases: MyBookingResponse[];
  onElegirDia?: (dia: string) => void;
}) {
  const [{ anio, mes }, setMes] = useState(() => mesInicial(clases));

  const porDia = useMemo(() => {
    const mapa = new Map<string, MyBookingResponse[]>();
    for (const clase of clases) {
      if (!clase.startsAt) continue;
      const dia = diaBogota(clase.startsAt);
      mapa.set(dia, [...(mapa.get(dia) ?? []), clase]);
    }
    return mapa;
  }, [clases]);

  const diasDelMes = new Date(anio, mes + 1, 0).getDate();
  // getDay() da 0 para domingo; aquí la semana arranca en lunes, así que el domingo pasa a 6.
  const huecoInicial = (new Date(anio, mes, 1).getDay() + 6) % 7;
  const hoy = claveDia(new Date().getFullYear(), new Date().getMonth(), new Date().getDate());

  function mover(paso: number) {
    const fecha = new Date(anio, mes + paso, 1);
    setMes({ anio: fecha.getFullYear(), mes: fecha.getMonth() });
  }

  return (
    <div className="rounded-card border border-border bg-surface-raised p-4">
      <div className="flex items-center justify-between">
        <button
          type="button"
          aria-label="Mes anterior"
          onClick={() => mover(-1)}
          className="grid h-9 w-9 place-items-center rounded-full text-text-secondary transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
        >
          <ChevronLeft size={18} strokeWidth={2} />
        </button>
        <p className="font-display text-[16px] font-bold capitalize">
          {MESES[mes]} {anio}
        </p>
        <button
          type="button"
          aria-label="Mes siguiente"
          onClick={() => mover(1)}
          className="grid h-9 w-9 place-items-center rounded-full text-text-secondary transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
        >
          <ChevronRight size={18} strokeWidth={2} />
        </button>
      </div>

      <div className="mt-3 grid grid-cols-7 gap-1">
        {DIAS_CORTOS.map((dia, i) => (
          <div
            key={i}
            aria-hidden="true"
            className="pb-1 text-center text-[11px] font-bold uppercase tracking-[0.04em] text-text-muted"
          >
            {dia}
          </div>
        ))}

        {Array.from({ length: huecoInicial }, (_, i) => (
          <div key={`hueco-${i}`} />
        ))}

        {Array.from({ length: diasDelMes }, (_, i) => {
          const dia = i + 1;
          const clave = claveDia(anio, mes, dia);
          const delDia = porDia.get(clave) ?? [];
          const esHoy = clave === hoy;
          const tieneClases = delDia.length > 0;

          return (
            <button
              key={clave}
              type="button"
              disabled={!tieneClases}
              onClick={() => onElegirDia?.(clave)}
              aria-label={
                tieneClases
                  ? `${dia}: ${delDia.length === 1 ? "1 clase" : `${delDia.length} clases`}`
                  : `${dia}, sin clases`
              }
              className={`flex min-h-[62px] flex-col items-center gap-1 rounded-base border p-1.5 text-center transition-colors ${
                tieneClases
                  ? "border-primary/35 bg-primary-soft/45 enabled:hover:bg-primary-soft"
                  : "border-transparent"
              } ${esHoy && !tieneClases ? "border-border-strong" : ""}`}
            >
              <span
                className={`text-[12.5px] tabular-nums ${
                  esHoy
                    ? "grid h-[22px] w-[22px] place-items-center rounded-full bg-night font-bold text-on-primary"
                    : tieneClases
                      ? "font-bold text-text"
                      : "font-semibold text-text-muted"
                }`}
              >
                {dia}
              </span>
              {/* La hora, no un punto: en un día con clase lo que se quiere saber es a qué hora. */}
              {delDia.slice(0, 2).map((clase) => (
                <span
                  key={clase.id}
                  className="w-full truncate text-[9.5px] font-bold leading-tight text-primary-strong"
                >
                  {horaBogota(clase.startsAt!)}
                </span>
              ))}
              {delDia.length > 2 && (
                <span className="text-[9.5px] font-bold text-text-muted">
                  +{delDia.length - 2}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
