"use client";

import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";

/**
 * Primitivos del sistema v2 "Amanecer cálido premium". Todo control interactivo es pill; el CTA
 * coral lleva la sombra tintada de amanecer (`shadow-primary`) y se hunde a `scale(.98)` al pulsar.
 * El foco es siempre el ring coral de `shadow-focus`, por fuera del borde.
 */

type Variante = "primario" | "secundario" | "contorno" | "tinta" | "fantasma" | "peligro";

const VARIANTES: Record<Variante, string> = {
  primario:
    "bg-primary text-on-primary shadow-primary hover:bg-primary-strong active:shadow-[0_4px_10px_rgba(232,80,58,0.28)]",
  secundario:
    "border-[1.5px] border-primary text-primary-strong hover:bg-primary-soft active:bg-[#fbd3cc] active:border-[#c93a26] active:text-[#a32a1d]",
  contorno: "border-[1.5px] border-border text-text hover:bg-surface-sunken",
  tinta: "bg-night text-on-primary hover:bg-[#3d2a63]",
  fantasma: "text-text-secondary font-semibold hover:bg-surface-sunken hover:text-text",
  peligro: "border-[1.5px] border-[#f0beb6] text-error hover:bg-error-bg",
};

export function Boton({
  variante = "primario",
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variante?: Variante }) {
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-pill px-6 text-[15px] font-bold transition-[transform,background-color,box-shadow,border-color,color] duration-[140ms] ease-standard focus-visible:shadow-focus active:scale-[0.98] disabled:pointer-events-none disabled:opacity-[0.42] ${VARIANTES[variante]} ${className}`}
    >
      {children}
    </button>
  );
}

/** CTA principal de pantalla: pill coral de 52 px, ancho completo. */
export function BotonPrincipal({
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <Boton variante="primario" {...props} className={`h-[52px] w-full ${className}`}>
      {children}
    </Boton>
  );
}

/** Spinner de carga para botones: 16 px, borde 2 px con la punta en el color del texto. */
export function Spinner({ className = "" }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
    />
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
        <span className="pointer-events-none absolute left-[18px] top-1/2 -translate-y-1/2 text-text-muted">
          {icono}
        </span>
      )}
      <input
        {...props}
        className={`h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised text-[15px] text-text transition-[border-color,box-shadow] placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none ${
          icono ? "pl-[46px] pr-4" : "px-[18px]"
        } ${className}`}
      />
    </div>
  );
}

/**
 * Chip seleccionable. La clave del sistema: cada grupo tiene su familia de color, para que el ojo
 * distinga «qué día» (durazno) de «qué hora» (lavanda) sin leer labels. El día elegido se marca en
 * tinta ciruela; la hora elegida, en coral con glow en bucle. Sin cupo: tachado y no interactivo.
 */
export function Chip({
  familia,
  activo,
  agotado,
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  familia: "fecha" | "hora";
  activo?: boolean;
  agotado?: boolean;
}) {
  const forma =
    "inline-flex min-h-11 items-center justify-center rounded-pill text-[13px] whitespace-nowrap transition-colors focus-visible:shadow-focus";
  const relleno = familia === "fecha" ? "px-[17px] py-[11px]" : "w-full px-2 py-[13px]";

  if (agotado) {
    return (
      <button
        type="button"
        disabled
        aria-disabled="true"
        className={`${forma} ${relleno} bg-[#f4efe9] font-semibold text-[#bbaf9f] line-through ${className}`}
        {...props}
      >
        {children}
      </button>
    );
  }

  const estado = activo
    ? familia === "fecha"
      ? "bg-night font-bold text-on-primary"
      : "bg-primary font-bold text-on-primary hora-elegida"
    : familia === "fecha"
      ? "bg-accent-peach-soft font-semibold text-[#8a5a33] hover:bg-[#ffdcbb]"
      : "bg-accent-lavender-soft font-semibold text-[#5e4a8a] hover:bg-[#e2d7f4]";

  return (
    <button type="button" {...props} className={`${forma} ${relleno} ${estado} ${className}`}>
      {children}
    </button>
  );
}

/** Sección titulada con color de familia: durazno, lavanda o menta, cada una con su icono. */
const BLOQUES = {
  melocoton: "bg-accent-peach-soft text-[#8a5a33]",
  lavanda: "bg-accent-lavender-soft text-[#5e4a8a]",
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
        <h2 className="flex items-center gap-1.5 text-[13px] font-bold uppercase tracking-[0.04em]">
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
    <div className={`rounded-card bg-surface-raised p-5 shadow-sm ${className}`}>{children}</div>
  );
}

/** Segmento de opciones (tabs, modalidad): pista hundida, opción activa elevada en blanco. */
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
    <div className="flex gap-1 rounded-pill bg-surface-sunken p-1">
      {opciones.map((opcion) => (
        <button
          key={opcion.valor}
          type="button"
          onClick={() => onCambio(opcion.valor)}
          className={`flex min-h-11 flex-1 items-center justify-center gap-1.5 rounded-pill py-2 text-[13px] transition-colors focus-visible:shadow-focus ${
            valor === opcion.valor
              ? "bg-surface-raised font-bold text-text shadow-[0_2px_8px_rgba(51,32,59,0.12)]"
              : "font-semibold text-text-muted hover:text-text"
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
      className={`relative h-[30px] w-[52px] shrink-0 rounded-pill p-[3px] transition-colors focus-visible:shadow-focus ${
        activo ? "bg-primary" : "bg-border"
      }`}
    >
      <span
        className="block h-6 w-6 rounded-full bg-white shadow-sm transition-transform duration-200 ease-standard"
        style={{ transform: activo ? "translateX(22px)" : "translateX(0)" }}
      />
    </button>
  );
}

/**
 * Badge píldora. Los tonos de estado llevan opcionalmente un punto de 7 px del color del texto;
 * los neutros (modalidad, nivel) van sin punto.
 */
const TONOS_BADGE = {
  menta: "bg-success-bg text-success",
  melocoton: "bg-warning-bg text-warning",
  lavanda: "bg-info-bg text-info",
  coral: "bg-primary-soft text-primary-strong",
  error: "bg-error-bg text-error",
  neutral: "bg-surface-sunken text-text-secondary",
} as const;

export function Badge({
  tono,
  punto,
  children,
}: {
  tono: keyof typeof TONOS_BADGE;
  punto?: boolean;
  children: ReactNode;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-pill px-3 py-1.5 text-[12px] font-bold ${TONOS_BADGE[tono]}`}
    >
      {punto && <span aria-hidden="true" className="h-[7px] w-[7px] rounded-full bg-current" />}
      {children}
    </span>
  );
}
