"use client";

import { useMemo } from "react";
import { Buscador } from "@/components/Buscador";
import { paises } from "@/lib/paises";

/**
 * El país, con su bandera, en un desplegable en el que se escribe para buscar (27/09/2026; antes era
 * un select de doscientas opciones). Los más frecuentes van primero mientras no se escribe nada.
 * Guarda el código ISO de dos letras, como siempre.
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
  const opciones = useMemo(
    () => paises().map((p) => ({ valor: p.code, etiqueta: p.nombre, prefijo: p.bandera })),
    [],
  );
  return (
    <Buscador
      id={id}
      opciones={opciones}
      valor={value}
      onElegir={onChange}
      placeholder="Escribe tu país"
      disabled={disabled}
      className={className}
    />
  );
}
