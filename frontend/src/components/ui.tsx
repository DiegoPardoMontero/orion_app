"use client";

import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";

/**
 * Primitivos del sistema "Aurora cálida". Todo control interactivo es pill (radius 999px);
 * el CTA coral lleva sombra táctil y "se hunde" al pulsarlo.
 */

type Variante = "coral" | "tinta" | "outline" | "peligro";

const VARIANTES: Record<Variante, string> = {
  coral:
    "bg-accent text-white shadow-[0_5px_0_var(--color-accent-pressed)] hover:bg-accent-strong active:translate-y-1 active:shadow-[0_1px_0_var(--color-accent-pressed)]",
  tinta: "bg-primary text-white hover:bg-primary-strong",
  outline: "border-[1.5px] border-border text-text hover:bg-surface-sunken",
  peligro: "bg-error text-white hover:bg-[#97201a]",
};

export function Boton({
  variante = "coral",
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variante?: Variante }) {
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex items-center justify-center gap-2 rounded-base px-4 text-sm font-bold transition-transform disabled:pointer-events-none disabled:bg-[#f5f2ea] disabled:text-text-disabled disabled:shadow-none ${VARIANTES[variante]} ${className}`}
    >
      {children}
    </button>
  );
}

/** CTA principal de pantalla: alto de 52 px, ancho completo. */
export function BotonPrincipal({
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <Boton variante="coral" {...props} className={`h-[52px] w-full text-[15px] ${className}`}>
      {children}
    </Boton>
  );
}

export function Campo({
  icono,
  className = "",
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { icono?: ReactNode }) {
  return (
    <div className="relative">
      {icono && (
        <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[#b3ada0]">
          {icono}
        </span>
      )}
      <input
        {...props}
        className={`w-full rounded-base border-[1.5px] border-border bg-surface-raised py-3 text-sm text-text placeholder:text-text-muted focus:border-accent ${
          icono ? "pl-11 pr-4" : "px-4"
        } ${className}`}
      />
    </div>
  );
}

/** Chip seleccionable. Los días se marcan en coral; las horas, en tinta con su pulso. */
export function Chip({
  activo,
  tono = "coral",
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { activo?: boolean; tono?: "coral" | "tinta" }) {
  const seleccionado =
    tono === "coral"
      ? "bg-accent text-white font-bold shadow-[0_3px_0_var(--color-accent-pressed)]"
      : "bg-primary text-white font-bold hora-elegida";

  return (
    <button
      type="button"
      {...props}
      className={`rounded-base px-3.5 py-2 text-[12.5px] transition-colors ${
        activo
          ? seleccionado
          : "border-[1.5px] border-border bg-surface-raised font-semibold text-text-secondary hover:bg-accent-bg"
      } ${className}`}
    >
      {children}
    </button>
  );
}

/** Bloque de sección con color: melocotón, lavanda o menta, cada uno con su icono. */
const BLOQUES = {
  melocoton: "bg-warning-bg text-warning",
  lavanda: "bg-info-bg text-info",
  menta: "bg-success-bg text-success",
} as const;

export function Bloque({
  tono,
  titulo,
  icono,
  extra,
  children,
}: {
  tono: keyof typeof BLOQUES;
  titulo: string;
  icono: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className={`rounded-card p-4 ${BLOQUES[tono]}`}>
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-1.5 text-[13.5px] font-bold">
          {icono}
          {titulo}
        </h2>
        {extra}
      </div>
      <div className="mt-3 text-text">{children}</div>
    </section>
  );
}

export function Tarjeta({
  className = "",
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={`rounded-card border-[1.5px] border-border-subtle bg-surface-raised p-4 ${className}`}
    >
      {children}
    </div>
  );
}

/** Segmento de dos opciones (tabs, modalidad): pista hundida, opción activa en tinta. */
export function Segmento<T extends string>({
  opciones,
  valor,
  onCambio,
}: {
  opciones: { valor: T; etiqueta: ReactNode }[];
  valor: T;
  onCambio: (valor: T) => void;
}) {
  return (
    <div className="flex gap-1 rounded-base bg-surface-sunken p-1">
      {opciones.map((opcion) => (
        <button
          key={opcion.valor}
          type="button"
          onClick={() => onCambio(opcion.valor)}
          className={`flex flex-1 items-center justify-center gap-1.5 rounded-base py-2 text-[13px] transition-colors ${
            valor === opcion.valor
              ? "bg-primary font-bold text-white"
              : "font-semibold text-text-secondary hover:bg-surface-raised"
          }`}
        >
          {opcion.etiqueta}
        </button>
      ))}
    </div>
  );
}

export function Toggle({
  activo,
  onCambio,
  etiqueta,
}: {
  activo: boolean;
  onCambio: (valor: boolean) => void;
  etiqueta: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={activo}
      aria-label={etiqueta}
      onClick={() => onCambio(!activo)}
      className={`relative h-[30px] w-[52px] shrink-0 rounded-base transition-colors ${
        activo ? "bg-success" : "bg-border"
      }`}
    >
      <span
        className="absolute top-[3px] h-6 w-6 rounded-full bg-white transition-[left] duration-150"
        style={{ left: activo ? 25 : 3 }}
      />
    </button>
  );
}

export function Badge({
  tono,
  children,
}: {
  tono: "menta" | "melocoton" | "lavanda" | "coral" | "error";
  children: ReactNode;
}) {
  const tonos = {
    menta: "bg-success-bg text-success",
    melocoton: "bg-warning-bg text-warning",
    lavanda: "bg-info-bg text-info",
    coral: "bg-accent-bg text-on-accent-bg",
    error: "bg-error-bg text-error",
  } as const;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-base px-2.5 py-1 text-[11.5px] font-bold ${tonos[tono]}`}
    >
      {children}
    </span>
  );
}
