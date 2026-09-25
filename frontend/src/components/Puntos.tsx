"use client";

import { Sparkles } from "lucide-react";
import { cifraDePuntos, useMisPuntos } from "@/lib/puntos";

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
