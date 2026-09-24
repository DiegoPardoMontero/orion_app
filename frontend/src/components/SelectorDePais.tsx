"use client";

import { ChevronDown } from "lucide-react";
import { FRECUENTES, paises } from "@/lib/paises";

/**
 * El país, de una lista con su bandera (24/09/2026). Los frecuentes arriba y después todos en
 * orden. Guarda el código ISO de dos letras, igual que el campo de texto de antes.
 */
export function SelectorDePais({
  id,
  value,
  onChange,
  className = "",
  disabled,
}: {
  id: string;
  value: string;
  onChange: (code: string) => void;
  className?: string;
  disabled?: boolean;
}) {
  const lista = paises();
  return (
    <div className={`relative ${className}`}>
      <select
        id={id}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        className="h-[52px] w-full appearance-none rounded-base border-[1.5px] border-border bg-surface-raised pl-[18px] pr-11 text-[15px] text-text transition-[border-color,box-shadow] focus:border-primary focus:shadow-focus focus:outline-none disabled:opacity-60"
      >
        {!value && <option value="">Elige tu país</option>}
        <optgroup label="Los más frecuentes">
          {lista.slice(0, FRECUENTES).map((p) => (
            <option key={p.code} value={p.code}>
              {p.bandera} {p.nombre}
            </option>
          ))}
        </optgroup>
        <optgroup label="Todos">
          {lista.slice(FRECUENTES).map((p) => (
            <option key={p.code} value={p.code}>
              {p.bandera} {p.nombre}
            </option>
          ))}
        </optgroup>
      </select>
      <ChevronDown
        size={18}
        strokeWidth={1.75}
        aria-hidden
        className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-text-muted"
      />
    </div>
  );
}
