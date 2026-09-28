"use client";

import { Lightbulb } from "lucide-react";
import { Rigel, type RigelPose } from "@/components/Rigel";
import type { RuleResponse } from "@/lib/api/types";
import { aMinutos, cuposDeTramo, cuposPorSemana, horasPorSemana, type FranjaDia } from "./franjas";

const INICIALES = ["L", "M", "M", "J", "V", "S", "D"];

/**
 * Rigel acompañando «Mis horarios» (Pardo, 27/09/2026: «que genere más motivación y esté más linda
 * visualmente»). La mascota se guarda para pantallas de marca y aquí entra por pedido expreso.
 *
 * <p>Lo que dice sale de las franjas y nada más: cuántos cupos de clase abre la semana, en cuántos
 * días y cuántas horas. El consejo de abajo es eso, un consejo —«quien trabaja de día suele buscar
 * clase en la noche»—, nunca una cifra de reservas que no tenemos.
 */
export function RigelDeHorarios({
  reglas,
  duracionClase,
  celebrando,
}: {
  reglas: RuleResponse[];
  duracionClase: number;
  /** Justo después de abrir una franja: Rigel lo celebra unos segundos. */
  celebrando: boolean;
}) {
  const franjas: FranjaDia[] = reglas.map((regla) => ({
    weekday: regla.weekday!,
    inicio: aMinutos(regla.startTime!),
    fin: aMinutos(regla.endTime!),
  }));
  const cupos = cuposPorSemana(franjas, duracionClase);
  const dias = new Set(franjas.map((franja) => franja.weekday));
  const horas = horasPorSemana(franjas);
  const vacio = franjas.length === 0;
  const pose: RigelPose = celebrando ? "celebracion" : vacio ? "saludo" : "profe";
  const cuposDeUnaNoche = cuposDeTramo(180, duracionClase);

  return (
    <section
      aria-label="Tu semana"
      className="relative overflow-hidden rounded-card bg-rigel-soft p-4 lg:p-5 xl:px-5 xl:pb-5 xl:pt-4"
    >
      {/* Un brillo cálido detrás de Rigel: el amarillo de la mascota fundiéndose con el durazno. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -left-10 -top-12 h-48 w-48 rounded-full bg-accent-peach/45 blur-2xl xl:left-1/2 xl:-translate-x-1/2"
      />

      <div className="relative flex items-center gap-3 xl:flex-col xl:items-stretch xl:gap-2">
        <Rigel pose={pose} decorativo className="h-24 w-auto shrink-0 lg:h-28 xl:h-36 xl:self-center" />

        {/* El globo de lo que dice: apunta a Rigel, a la izquierda o arriba según haya espacio. */}
        <div
          aria-live="polite"
          className="relative min-w-0 flex-1 rounded-card bg-surface-raised px-4 py-3 shadow-sm before:absolute before:-left-1.5 before:top-1/2 before:h-3.5 before:w-3.5 before:-translate-y-1/2 before:rotate-45 before:bg-surface-raised xl:px-5 xl:py-4 xl:before:-top-1.5 xl:before:left-1/2 xl:before:-translate-x-1/2 xl:before:translate-y-0"
        >
          <p className="relative text-[11px] font-bold uppercase tracking-[0.1em] text-rigel-ink">
            {celebrando ? "¡Listo!" : "Tu semana"}
          </p>

          {vacio ? (
            <>
              <p className="relative mt-0.5 font-display text-[18px] font-bold leading-tight text-text xl:text-[20px]">
                Abre tu primera franja
              </p>
              <p className="relative mt-1 text-[12.5px] leading-snug text-text-secondary">
                <span className="lg:hidden">Toca + en un día y elige desde y hasta qué hora das clase. </span>
                <span className="hidden lg:inline">
                  Arrastra sobre un día, desde la hora en que empiezas hasta la que terminas.{" "}
                </span>
                Una franja de 6 a 9 PM ya abre {cuposDeUnaNoche} cupos cada semana.
              </p>
            </>
          ) : (
            <>
              <p className="relative mt-0.5 flex items-baseline gap-1.5 font-display font-bold leading-none text-text">
                <span className="text-[30px] tabular-nums xl:text-[40px]">{cupos}</span>
                <span className="text-[16px] xl:text-[18px]">{cupos === 1 ? "cupo de clase" : "cupos de clase"}</span>
              </p>
              <p className="relative mt-1 text-[12.5px] leading-snug text-text-secondary">
                abres cada semana, en {dias.size} {dias.size === 1 ? "día" : "días"} ·{" "}
                {new Intl.NumberFormat("es-CO", { maximumFractionDigits: 1 }).format(horas)}{" "}
                {horas === 1 ? "hora" : "horas"}
              </p>
            </>
          )}

          <ol aria-hidden="true" className="relative mt-2.5 flex gap-1">
            {INICIALES.map((inicial, i) => (
              <li
                key={i}
                className={`grid h-6 w-6 place-items-center rounded-full text-[10.5px] font-bold ${
                  dias.has(i + 1) ? "bg-primary text-on-primary" : "bg-surface-sunken text-text-muted"
                }`}
              >
                {inicial}
              </li>
            ))}
          </ol>
        </div>
      </div>

      {/* El consejo y la tranquilidad, solo donde hay espacio: en el celular, la lista va primero. */}
      <div className="relative mt-4 hidden space-y-2.5 lg:block">
        <p className="flex gap-2 rounded-base bg-surface-raised/70 p-3 text-[12.5px] leading-snug text-text">
          <Lightbulb size={16} strokeWidth={2} className="mt-px shrink-0 text-rigel-ink" aria-hidden="true" />
          {consejoPara(franjas, duracionClase)}
        </p>
        <p className="px-1 text-[11.5px] leading-snug text-rigel-ink">
          Mover o borrar una franja no cancela las clases que ya te reservaron.
        </p>
      </div>
    </section>
  );
}

/**
 * Un consejo según lo que falta en el horario. Son sugerencias razonables sobre a quién le sirve
 * cada hora, no estadísticas de Orión: por eso dicen «suele» y no «el 60 %».
 */
function consejoPara(franjas: FranjaDia[], duracionClase: number): string {
  const porMediaHora = `Cada media hora que le sumas a una franja abre un cupo más: de 6 a 9 PM son ${cuposDeTramo(180, duracionClase)}; de 6 a 10 PM, ${cuposDeTramo(240, duracionClase)}.`;
  if (franjas.length === 0) return porMediaHora;
  if (!franjas.some((franja) => franja.fin > 18 * 60)) {
    return "Quien trabaja de día suele buscar clase después de las 6 PM. Una franja en la noche te abre a esas personas.";
  }
  if (!franjas.some((franja) => franja.weekday >= 6)) {
    return "El fin de semana es cuando muchos adultos tienen tiempo para estudiar. ¿Te sirve una franja el sábado en la mañana?";
  }
  if (!franjas.some((franja) => franja.inicio < 9 * 60)) {
    return "Hay quien prefiere estudiar antes de ir a trabajar. Una franja temprano, de 6 a 8 AM, llega a esas personas.";
  }
  return porMediaHora;
}
