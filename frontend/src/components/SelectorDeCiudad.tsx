"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { Buscador } from "@/components/Buscador";

/**
 * La ciudad, del catálogo del país elegido (public/ciudades/<PAÍS>.json, de GeoNames): primero el
 * país y después la ciudad (Pardo, 27/09/2026). Si la suya no está, se puede escribir: el catálogo
 * de otros países solo trae las de más de 15.000 habitantes.
 */
export function SelectorDeCiudad({
  id,
  pais,
  value,
  onChange,
  className = "",
  disabled,
}: {
  id: string;
  /** Código ISO del país; sin él, el campo espera a que se elija. */
  pais: string;
  value: string;
  onChange: (ciudad: string) => void;
  className?: string;
  disabled?: boolean;
}) {
  const ciudades = useQuery({
    queryKey: ["ciudades", pais],
    queryFn: async () => {
      const r = await fetch(`/ciudades/${pais}.json`);
      // Un país sin catálogo (pocos, y muy pequeños) deja escribir la ciudad a mano.
      if (!r.ok) return [] as [string, string][];
      return (await r.json()) as [string, string][];
    },
    enabled: /^[A-Z]{2}$/.test(pais),
    staleTime: Infinity,
  });

  // Dos ciudades con el mismo nombre en regiones distintas se distinguen por la región, pero se guarda
  // solo el nombre: es lo que el perfil muestra («Bogotá, 🇨🇴 Colombia»).
  const opciones = useMemo(() => {
    const vistas = new Set<string>();
    return (ciudades.data ?? []).flatMap(([nombre, region]) => {
      if (vistas.has(nombre)) return [];
      vistas.add(nombre);
      return [{ valor: nombre, etiqueta: nombre, detalle: region || undefined }];
    });
  }, [ciudades.data]);

  return (
    <Buscador
      id={id}
      opciones={opciones}
      valor={value}
      onElegir={onChange}
      placeholder={pais ? "Escribe tu ciudad" : "Primero elige tu país"}
      libre
      cargando={ciudades.isPending && !!pais}
      disabled={disabled || !pais}
      className={className}
    />
  );
}
