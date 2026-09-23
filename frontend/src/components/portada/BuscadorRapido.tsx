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
    <div className="rounded-card bg-surface-raised p-5 shadow-md lg:p-7">
      <div className="grid gap-5 lg:grid-cols-[1fr_1fr_auto] lg:items-end">
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
          className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Ver profesores disponibles
          <ArrowRight size={18} strokeWidth={1.9} />
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
      <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-text-secondary">{titulo}</p>
      <div role="radiogroup" aria-label={titulo} className="mt-2 flex flex-wrap gap-2">
        {opciones.map((opcion) => {
          const activa = opcion.id === elegida;
          return (
            <button
              key={opcion.id}
              type="button"
              role="radio"
              aria-checked={activa}
              onClick={() => onElegir(opcion.id)}
              className={`min-h-11 rounded-pill px-4 text-[14px] font-semibold transition-colors focus-visible:shadow-focus ${
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
