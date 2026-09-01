"use client";

import { ChevronDown } from "lucide-react";
import { useState } from "react";
import { componerE164, PAISES, parseTelefono } from "@/lib/phone";

/**
 * Teléfono con selector de país. Produce y consume E.164 (`+573001112233`). Sin librerías: lista
 * curada de países (LatAm + US + ES), Colombia por defecto. El componente se inicializa del valor
 * y a partir de ahí gestiona país y número, emitiendo el E.164 al padre; número vacío → "".
 * La lógica pura (parseo/composición) vive en `lib/phone` para poder testearla sin React.
 */
export function PhoneInput({
  value,
  onChange,
  id,
  placeholder,
  className = "",
}: {
  value?: string;
  onChange: (e164: string) => void;
  id?: string;
  placeholder?: string;
  className?: string;
}) {
  const inicial = parseTelefono(value);
  const [dial, setDial] = useState(inicial.dial);
  const [local, setLocal] = useState(inicial.local);

  function emitir(nuevoDial: string, nuevoLocal: string) {
    onChange(componerE164(nuevoDial, nuevoLocal));
  }

  return (
    <div className={`flex gap-2 ${className}`}>
      <div className="relative shrink-0">
        <select
          aria-label="País"
          value={dial}
          onChange={(event) => {
            setDial(event.target.value);
            emitir(event.target.value, local);
          }}
          className="h-[52px] appearance-none rounded-base border-[1.5px] border-border bg-surface-raised pl-3.5 pr-9 text-[15px] font-semibold text-text transition-[border-color,box-shadow] focus:border-primary focus:shadow-focus focus:outline-none"
        >
          {PAISES.map((p) => (
            <option key={p.code} value={p.dial}>
              {p.flag} +{p.dial}
            </option>
          ))}
        </select>
        <ChevronDown
          size={16}
          strokeWidth={1.75}
          className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-text-muted"
        />
      </div>
      <input
        id={id}
        type="tel"
        inputMode="numeric"
        autoComplete="tel-national"
        placeholder={placeholder ?? "300 111 2233"}
        value={local}
        onChange={(event) => {
          const digits = event.target.value.replace(/\D/g, "");
          setLocal(digits);
          emitir(dial, digits);
        }}
        className="h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised px-[18px] text-[15px] text-text placeholder:text-text-muted transition-[border-color,box-shadow] focus:border-primary focus:shadow-focus focus:outline-none"
      />
    </div>
  );
}
