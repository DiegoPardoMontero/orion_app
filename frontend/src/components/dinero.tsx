"use client";

import type { ReactNode } from "react";
import { precioCop } from "@/lib/format";

/**
 * Piezas compartidas por las pantallas de dinero (checkout, saldo, ganancias, conciliación).
 * Están juntas porque una cifra de plata tiene que verse igual en todas: si el desglose del
 * checkout y el de las ganancias divergen, el usuario deja de creerse los dos.
 */

/** Una línea de desglose: concepto a la izquierda, importe a la derecha, tabular para que cuadre. */
export function LineaImporte({
  etiqueta,
  valor,
  tono = "normal",
}: {
  etiqueta: ReactNode;
  valor: ReactNode;
  tono?: "normal" | "credito" | "total";
}) {
  const estilos = {
    normal: "text-text-secondary",
    credito: "text-success font-semibold",
    total: "border-t border-border pt-2 mt-2 font-bold text-text",
  }[tono];

  return (
    <div className={`flex items-baseline justify-between gap-4 py-1 ${estilos}`}>
      <span>{etiqueta}</span>
      <span className="tabular-nums whitespace-nowrap">{valor}</span>
    </div>
  );
}

/** Cifra grande con su etiqueta: los tres estados del dinero de un profesor, el saldo, el total. */
export function Cifra({
  tono,
  icono,
  valorCop,
  etiqueta,
  ayuda,
}: {
  tono: "menta" | "melocoton" | "lavanda" | "coral";
  icono: ReactNode;
  valorCop: number;
  etiqueta: string;
  ayuda?: string;
}) {
  const TONOS = {
    menta: "bg-success-bg text-success",
    melocoton: "bg-warning-bg text-warning",
    lavanda: "bg-info-bg text-info",
    coral: "bg-primary-soft text-primary-strong",
  } as const;

  return (
    <div className="rounded-card bg-surface-raised p-4 shadow-sm">
      <span
        aria-hidden="true"
        className={`inline-grid h-9 w-9 place-items-center rounded-full ${TONOS[tono]}`}
      >
        {icono}
      </span>
      <p className="mt-3 font-display text-h2 font-bold tabular-nums text-text">
        {precioCop(valorCop)}
      </p>
      <p className="text-[13px] font-semibold text-text-secondary">{etiqueta}</p>
      {ayuda && <p className="mt-1 text-[11.5px] text-text-muted">{ayuda}</p>}
    </div>
  );
}
