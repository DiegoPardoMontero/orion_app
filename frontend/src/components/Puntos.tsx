"use client";

import { Sparkles } from "lucide-react";
import { fechaRelativa } from "@/lib/format";
import { cifraDePuntos, nombreDeLaManera, queDioPuntos, useMisPuntos } from "@/lib/puntos";

/**
 * Los puntos junto al nombre (24/09/2026: «muéstralo por todo lado»). Dorado de Rigel: no es el
 * coral de la acción ni el durazno de las estrellas, y así se lee como una cifra propia.
 */
export function ChipPuntos({ total, compacto = false, className = "" }: { total: number; compacto?: boolean; className?: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-pill bg-rigel-soft font-bold text-rigel-ink tabular-nums ${
        compacto ? "px-2 py-0.5 text-[11.5px]" : "px-2.5 py-1 text-[12.5px]"
      } ${className}`}
      title="Tus puntos en Orión"
    >
      <Sparkles size={compacto ? 11 : 13} strokeWidth={2.4} className="text-rigel-edge" aria-hidden />
      {cifraDePuntos(total)}
      {!compacto && <span className="font-semibold"> puntos</span>}
      {compacto && <span className="sr-only"> puntos</span>}
    </span>
  );
}

/** El chip con sus propios datos. No pinta nada hasta tenerlos: un «0» que luego salta a 340 confunde. */
export function MisPuntosChip({ compacto = false, className = "" }: { compacto?: boolean; className?: string }) {
  const { data } = useMisPuntos();
  if (!data) return null;
  return <ChipPuntos total={data.total} compacto={compacto} className={className} />;
}

/**
 * La tarjeta de «Tus puntos» en el perfil: el total, lo último que dio puntos y todas las maneras
 * de hacer más, con la cifra que da cada una (sale del backend: la pantalla nunca promete otra).
 */
export function TarjetaPuntos() {
  const { data } = useMisPuntos();
  if (!data) return null;

  return (
    <section id="puntos" aria-labelledby="titulo-puntos" className="mt-4 scroll-mt-24 rounded-card bg-surface-raised p-5 shadow-sm lg:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="titulo-puntos" className="font-display text-[18px] font-bold">
          Tus puntos
        </h2>
        <p className="flex items-baseline gap-1.5 font-display text-[30px] font-extrabold leading-none text-rigel-ink tabular-nums">
          <Sparkles size={20} strokeWidth={2.4} className="self-center text-rigel-edge" aria-hidden />
          {cifraDePuntos(data.total)}
        </p>
      </div>
      <p className="mt-1 text-[13.5px] leading-relaxed text-text-secondary">
        No compran nada: cuentan cuánto has recorrido en Orión. Salen junto a tu nombre.
      </p>

      <div className="mt-4 grid gap-5 md:grid-cols-2">
        <div>
          <h3 className="text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">Lo último</h3>
          {data.recent.length === 0 ? (
            <p className="mt-2 text-[13.5px] text-text-secondary">
              Todavía nada. Tu primera clase da 25 de golpe.
            </p>
          ) : (
            <ul className="mt-2 flex flex-col divide-y divide-surface-sunken">
              {data.recent.map((m, i) => (
                <li key={`${m.source}-${m.occurredAt}-${i}`} className="flex items-baseline justify-between gap-3 py-2">
                  <span className="min-w-0">
                    <span className="block truncate text-[13.5px] font-semibold text-text">{queDioPuntos(m)}</span>
                    <span className="block text-[12px] text-text-muted">{fechaRelativa(m.occurredAt)}</span>
                  </span>
                  <span className="shrink-0 text-[13.5px] font-bold text-rigel-ink tabular-nums">+{m.points}</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <h3 className="text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">Cómo se hacen</h3>
          <ul className="mt-2 flex flex-col divide-y divide-surface-sunken">
            {data.ways.map((w) => (
              <li key={w.source} className="flex items-baseline justify-between gap-3 py-2">
                <span className="min-w-0 text-[13.5px] text-text">
                  {nombreDeLaManera(w.source)}
                  {w.once && <span className="text-text-muted"> · una vez</span>}
                </span>
                <span className="shrink-0 text-[13.5px] font-bold text-rigel-ink tabular-nums">+{w.points}</span>
              </li>
            ))}
            <li className="flex items-baseline justify-between gap-3 py-2">
              <span className="min-w-0 text-[13.5px] text-text">Cada estrella de «Mi cielo»</span>
              <span className="shrink-0 text-[13px] font-bold text-rigel-ink">la suya</span>
            </li>
          </ul>
        </div>
      </div>
    </section>
  );
}
