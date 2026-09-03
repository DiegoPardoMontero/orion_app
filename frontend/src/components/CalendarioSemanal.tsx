"use client";

import { X } from "lucide-react";
import type { RuleResponse } from "@/lib/api/types";
import { hora12Compacta, rangoCompacto } from "@/lib/format";

/** ISO 1–7, igual que el backend: 1 = lunes … 7 = domingo. */
export const DIAS_SEMANA = [
  { valor: 1, nombre: "Lunes", corto: "Lun" },
  { valor: 2, nombre: "Martes", corto: "Mar" },
  { valor: 3, nombre: "Miércoles", corto: "Mié" },
  { valor: 4, nombre: "Jueves", corto: "Jue" },
  { valor: 5, nombre: "Viernes", corto: "Vie" },
  { valor: 6, nombre: "Sábado", corto: "Sáb" },
  { valor: 7, nombre: "Domingo", corto: "Dom" },
];

const ALTO_HORA = 44;
const HORA_MINIMA = 6;
const HORA_MAXIMA = 23;

/** "18:00" → 18. Las franjas siempre empiezan y terminan en punto; lo garantiza el backend. */
function hora(hhmm: string): number {
  return Number(hhmm.slice(0, 2));
}

/**
 * La ventana que se dibuja. No son las 24 horas: un profesor que enseña de 6 a 9 de la tarde no
 * necesita mirar la madrugada, y pintarla entera dejaría su horario reducido a una franja diminuta
 * en un lienzo casi vacío. Se toma lo que ocupan sus reglas, con un margen de una hora arriba y
 * abajo para que quepa añadir al lado de lo que ya hay.
 */
function ventana(reglas: RuleResponse[]): { desde: number; hasta: number } {
  if (reglas.length === 0) return { desde: 7, hasta: 21 };
  const inicios = reglas.map((r) => hora(r.startTime!));
  const fines = reglas.map((r) => hora(r.endTime!));
  return {
    desde: Math.max(HORA_MINIMA, Math.min(...inicios) - 1),
    hasta: Math.min(HORA_MAXIMA, Math.max(...fines) + 1),
  };
}

/**
 * El horario semanal como lo que es: una rejilla de horas por días. La versión anterior lo daba
 * como siete listas de etiquetas, y ahí "6–9 PM" y "8–11 AM" pesaban lo mismo aunque una dure el
 * triple; aquí la duración es la altura y el hueco entre dos franjas se ve sin leer nada.
 */
export function CalendarioSemanal({
  reglas,
  onAnadir,
  onEliminar,
}: {
  reglas: RuleResponse[];
  onAnadir: (weekday: number, horaInicio: string) => void;
  onEliminar: (regla: RuleResponse) => void;
}) {
  const { desde, hasta } = ventana(reglas);
  const filas = hasta - desde;

  return (
    <div className="overflow-x-auto rounded-card border border-border bg-surface-raised">
      <div className="min-w-[640px]">
        {/* Cabecera de días, alineada con la columna de horas mediante el mismo grid. */}
        <div className="grid grid-cols-[52px_repeat(7,1fr)] border-b border-border">
          <div />
          {DIAS_SEMANA.map((dia) => (
            <div
              key={dia.valor}
              className="border-l border-border py-2 text-center text-[12.5px] font-bold text-text-secondary"
            >
              {dia.corto}
            </div>
          ))}
        </div>

        <div
          className="relative grid grid-cols-[52px_repeat(7,1fr)]"
          style={{ height: filas * ALTO_HORA }}
        >
          {/* Columna de horas: la etiqueta se sube media línea para quedar sobre su divisoria. */}
          <div className="relative">
            {Array.from({ length: filas }, (_, i) => (
              <div
                key={i}
                className="absolute right-2 -translate-y-1/2 text-[11px] font-semibold tabular-nums text-text-muted"
                style={{ top: i * ALTO_HORA }}
              >
                {hora12Compacta(`${String(desde + i).padStart(2, "0")}:00`)}
              </div>
            ))}
          </div>

          {DIAS_SEMANA.map((dia) => {
            const delDia = reglas.filter((regla) => regla.weekday === dia.valor);
            return (
              <div key={dia.valor} className="relative border-l border-border">
                {/* Celdas vacías: cada una añade una franja de una hora justo ahí, que es lo que
                    el profesor quiere decir al pulsar sobre las 7 de la tarde de un martes. */}
                {Array.from({ length: filas }, (_, i) => {
                  const h = desde + i;
                  const ocupada = delDia.some(
                    (regla) => hora(regla.startTime!) <= h && h < hora(regla.endTime!),
                  );
                  return (
                    <button
                      key={i}
                      type="button"
                      disabled={ocupada}
                      aria-label={`Añadir franja el ${dia.nombre.toLowerCase()} a las ${hora12Compacta(`${String(h).padStart(2, "0")}:00`)}`}
                      onClick={() => onAnadir(dia.valor, `${String(h).padStart(2, "0")}:00`)}
                      className="absolute inset-x-0 border-t border-border/60 transition-colors enabled:hover:bg-primary-soft/60 disabled:cursor-default"
                      style={{ top: i * ALTO_HORA, height: ALTO_HORA }}
                    />
                  );
                })}

                {delDia.map((regla) => {
                  const inicio = hora(regla.startTime!);
                  const fin = hora(regla.endTime!);
                  return (
                    <div
                      key={regla.id}
                      className="absolute inset-x-1 flex flex-col justify-between overflow-hidden rounded-base bg-night px-2 py-1.5 text-on-primary"
                      style={{
                        top: (inicio - desde) * ALTO_HORA + 2,
                        height: (fin - inicio) * ALTO_HORA - 4,
                      }}
                    >
                      <span className="text-[11.5px] font-bold leading-tight">
                        {rangoCompacto(regla.startTime!, regla.endTime!)}
                      </span>
                      <button
                        type="button"
                        aria-label={`Eliminar la franja de ${rangoCompacto(regla.startTime!, regla.endTime!)} el ${dia.nombre.toLowerCase()}`}
                        onClick={() => onEliminar(regla)}
                        className="grid h-5 w-5 shrink-0 place-items-center self-end rounded-full bg-white/20 transition-colors hover:bg-primary"
                      >
                        <X size={11} strokeWidth={2.6} />
                      </button>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
