"use client";

import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { HORARIOS, OBJETIVOS, rutaDelBuscador, type HorarioId, type ObjetivoId } from "./buscador";

/**
 * El buscador rápido de la portada: dos preguntas y al directorio, sin cuenta. Las dos son
 * opcionales; volver a tocar una opción la desmarca.
 */
export function BuscadorRapido() {
  const [objetivo, setObjetivo] = useState<ObjetivoId | null>(null);
  const [horario, setHorario] = useState<HorarioId | null>(null);

  return (
    // Compacto (Pardo, 23/09/2026): cada grupo en una sola línea y el botón al lado, en escritorio.
    <div className="rounded-card bg-surface-raised px-5 py-4 shadow-md lg:px-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:flex-wrap lg:items-end lg:gap-x-6 lg:gap-y-3 xl:grid xl:grid-cols-[auto_auto_auto] xl:justify-between xl:gap-x-5">
        <Grupo
          titulo="¿Para qué lo necesitas?"
          opciones={OBJETIVOS}
          elegida={objetivo}
          onElegir={(id) => setObjetivo(id === objetivo ? null : (id as ObjetivoId))}
        />
        <Grupo
          titulo="¿Cuándo puedes?"
          opciones={HORARIOS}
          elegida={horario}
          onElegir={(id) => setHorario(id === horario ? null : (id as HorarioId))}
        />
        <Link
          href={rutaDelBuscador(objetivo, horario)}
          className="inline-flex h-11 shrink-0 items-center whitespace-nowrap justify-center gap-2 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus lg:ml-auto lg:px-4 xl:justify-self-end"
        >
          Ver profesores disponibles
          <ArrowRight size={17} strokeWidth={1.9} />
        </Link>
      </div>
    </div>
  );
}

function Grupo({
  titulo,
  opciones,
  elegida,
  onElegir,
}: {
  titulo: string;
  opciones: readonly { id: string; etiqueta: string }[];
  elegida: string | null;
  onElegir: (id: string) => void;
}) {
  return (
    <div>
      <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-text-secondary">{titulo}</p>
      <div role="radiogroup" aria-label={titulo} className="mt-1.5 flex flex-wrap gap-1.5 xl:flex-nowrap">
        {opciones.map((opcion) => {
          const activa = opcion.id === elegida;
          return (
            <button
              key={opcion.id}
              type="button"
              role="radio"
              aria-checked={activa}
              onClick={() => onElegir(opcion.id)}
              className={`min-h-10 whitespace-nowrap rounded-pill px-3.5 text-[13.5px] font-semibold transition-colors focus-visible:shadow-focus lg:min-h-9 lg:px-3 xl:px-2.5 ${
                activa
                  ? "bg-night text-on-primary"
                  : "border-[1.5px] border-border bg-surface text-text hover:bg-surface-sunken"
              }`}
            >
              {opcion.etiqueta}
            </button>
          );
        })}
      </div>
    </div>
  );
}
