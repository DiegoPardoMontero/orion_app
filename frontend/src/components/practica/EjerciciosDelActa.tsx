"use client";

import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { Badge, Spinner, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import {
  leerPayload,
  mostrarEsperada,
  NOMBRE_DEL_TIPO,
  type EjercicioDelActa,
  type PracticaDelActa,
} from "@/lib/practica";

const ESTADO: Record<PracticaDelActa["status"], string> = {
  PENDING: "Se están generando. Suelen estar listos en un minuto.",
  READY: "Listos. Tu estudiante todavía no los empieza.",
  IN_PROGRESS: "Tu estudiante los está haciendo.",
  COMPLETED: "Tu estudiante ya los terminó.",
  EXPIRED: "Vencieron sin terminarse.",
  FAILED:
    "Esta vez no salieron ejercicios: el acta no tenía palabras ni frases concretas en que anclarlos. " +
    "La próxima, anota el vocabulario y los errores de la clase.",
};

/**
 * Los ejercicios que salieron del acta, como los ve el profesor que la escribió: solo lectura, con
 * la respuesta esperada y la explicación. Nunca lo que respondió el estudiante —el servidor no lo
 * manda—: equivocarse en privado es lo que hace útil la práctica.
 */
export function EjerciciosDelActa({ actaId }: { actaId: string }) {
  const practica = useQuery({
    queryKey: ["lesson-note-practice", actaId],
    queryFn: async () =>
      (await apiFetch<PracticaDelActa | undefined>(`/api/v1/professors/me/lesson-notes/${actaId}/practice`)) ?? null,
    refetchInterval: (q) => (q.state.data?.status === "PENDING" ? 15_000 : false),
  });

  // Sin set (la práctica está apagada) no hay nada que mostrar, ni siquiera el título.
  if (!practica.data) return null;
  const p = practica.data;

  return (
    <Tarjeta>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-display text-h3 font-bold">
          <Sparkles size={18} strokeWidth={2} className="text-primary" />
          La práctica que salió de esta acta
        </h2>
        {p.items.length > 0 && <Badge tono="lavanda">{p.items.length} ejercicios</Badge>}
      </div>
      <p className="mt-1.5 flex items-center gap-2 text-[13.5px] text-text-secondary">
        {p.status === "PENDING" && <Spinner />}
        {ESTADO[p.status]}
      </p>

      {p.items.length > 0 && (
        <>
          <ol className="mt-4 grid gap-3">
            {p.items.map((e) => (
              <li key={e.index} className="rounded-card bg-surface-sunken p-4">
                <Ejercicio ejercicio={e} />
              </li>
            ))}
          </ol>
          <p className="mt-3 text-[12.5px] text-text-muted">
            Tu estudiante los ve uno a uno y sin las respuestas. Tú no ves lo que responde: equivocarse en privado es lo
            que hace útil la práctica.
          </p>
        </>
      )}
    </Tarjeta>
  );
}

function Ejercicio({ ejercicio: e }: { ejercicio: EjercicioDelActa }) {
  return (
    <div className="grid gap-2 text-[13.5px]">
      <p className="flex flex-wrap items-center gap-2 text-[11.5px] font-bold uppercase tracking-[0.08em] text-text-muted">
        {e.index + 1}. {NOMBRE_DEL_TIPO[e.type]}
        {e.sourceTerm && (
          <span className="rounded-pill bg-surface px-2 py-0.5 normal-case tracking-normal text-text-secondary">
            {e.sourceTerm}
          </span>
        )}
      </p>
      <p className="font-semibold text-text">{e.prompt}</p>
      <Material ejercicio={e} />
      <p className="text-text-secondary">
        <span className="font-bold text-text">Respuesta: </span>
        {e.expected
          ? mostrarEsperada(e.type, e.expected)
          : e.type === "WRITE_SENTENCE"
            ? "cualquier frase suya que la use bien."
            : "—"}
      </p>
      {e.explanation && <p className="text-text-secondary">{e.explanation}</p>}
    </div>
  );
}

/** Lo que el estudiante tiene delante para resolverlo: la frase, las opciones, los pares o el diálogo. */
function Material({ ejercicio: e }: { ejercicio: EjercicioDelActa }) {
  switch (e.type) {
    case "FILL_BLANK": {
      const p = leerPayload<{ sentence?: string; options?: string[] }>(e);
      return (
        <p className="text-text-secondary">
          «{p.sentence}»{p.options?.length ? ` · Opciones: ${p.options.join(", ")}` : ""}
        </p>
      );
    }
    case "FIX_SENTENCE": {
      const p = leerPayload<{ sentence?: string; accepted?: string[] }>(e);
      return (
        <p className="text-text-secondary">
          «{p.sentence}»{p.accepted?.length ? ` · También vale: ${p.accepted.join(" / ")}` : ""}
        </p>
      );
    }
    case "MATCH_MEANING": {
      const p = leerPayload<{ terms?: string[] }>(e);
      return p.terms?.length ? <p className="text-text-secondary">Palabras: {p.terms.join(", ")}</p> : null;
    }
    case "ORDER_DIALOGUE": {
      const p = leerPayload<{ lines?: string[] }>(e);
      return p.lines?.length ? (
        <p className="text-text-secondary">Le llegan desordenadas: {p.lines.map((l) => `«${l}»`).join(" ")}</p>
      ) : null;
    }
    case "WRITE_SENTENCE":
      return null;
  }
}
