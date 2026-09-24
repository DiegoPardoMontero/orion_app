"use client";

import { useQuery } from "@tanstack/react-query";
import { Check, CornerDownRight, SkipForward, Sparkles } from "lucide-react";
import { Spinner, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import { fechaLarga } from "@/lib/format";
import {
  leerPayload,
  mostrarEsperada,
  mostrarRespuesta,
  NOMBRE_DE_CATEGORIA,
  NOMBRE_DEL_TIPO,
  primerNombre,
  resultadoDe,
  type EjercicioDelActa,
  type PracticaDelActa,
  type ResultadoDelEjercicio,
} from "@/lib/practica";

const ESTADO: Record<PracticaDelActa["status"], string> = {
  PENDING: "Se están generando. Suelen estar listos en un minuto.",
  READY: "Listos. Todavía no los empieza.",
  IN_PROGRESS: "Los está haciendo.",
  COMPLETED: "Los terminó.",
  EXPIRED: "Vencieron sin terminarse.",
  FAILED:
    "Esta vez no salieron ejercicios: el acta no tenía palabras ni frases concretas en que anclarlos. " +
    "La próxima, anota el vocabulario y los errores de la clase.",
};

/** Verde el primer intento, ámbar el segundo, neutro lo mostrado o saltado. Nunca rojo. */
const RESULTADO: Record<ResultadoDelEjercicio, { texto: string; clase: string }> = {
  primero: { texto: "Al primer intento", clase: "bg-success-bg text-success" },
  segundo: { texto: "Al segundo intento", clase: "bg-warning-bg text-warning" },
  mostrada: { texto: "Se le mostró la respuesta", clase: "bg-surface text-text-secondary" },
  saltado: { texto: "Lo saltó (sin voz en su dispositivo)", clase: "bg-surface text-text-secondary" },
  sinHacer: { texto: "Sin hacer", clase: "bg-surface text-text-muted" },
};

/**
 * La práctica que salió del acta, para el profesor que la escribió: cada ejercicio con su respuesta
 * esperada y lo que hizo su estudiante —lo que respondió en cada intento y cómo le fue—, para
 * preparar la siguiente clase. El estudiante lo sabe: se lo dice la portada de su práctica.
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
  const nombre = p.studentName ? primerNombre(p.studentName) : "tu estudiante";
  const empezada = p.status === "IN_PROGRESS" || p.status === "COMPLETED" || p.status === "EXPIRED";
  const leCosto = Array.from(
    new Set(p.items.filter((e) => resultadoDe(e) === "mostrada").map((e) => e.sourceTerm ?? NOMBRE_DEL_TIPO[e.type])),
  );

  return (
    <Tarjeta>
      <h2 className="flex items-center gap-2 font-display text-h3 font-bold">
        <Sparkles size={18} strokeWidth={2} className="text-primary" />
        {empezada ? `Cómo le fue a ${nombre}` : "La práctica que salió de esta acta"}
      </h2>
      <p className="mt-1.5 flex items-center gap-2 text-[13.5px] text-text-secondary">
        {p.status === "PENDING" && <Spinner />}
        {p.status === "COMPLETED" && p.completedAt ? `Los terminó el ${fechaLarga(p.completedAt)}.` : ESTADO[p.status]}
      </p>

      {empezada && (
        <div className="mt-3 flex flex-wrap gap-1.5 text-[12.5px] font-bold">
          <span className="rounded-pill bg-success-bg px-3 py-1 text-success">{p.firstTry} al primer intento</span>
          <span className="rounded-pill bg-warning-bg px-3 py-1 text-warning">{p.secondTry} al segundo</span>
          <span className="rounded-pill bg-surface-sunken px-3 py-1 text-text-secondary">{p.shown} se le mostró</span>
          {p.skipped > 0 && (
            <span className="rounded-pill bg-surface-sunken px-3 py-1 text-text-secondary">{p.skipped} saltados</span>
          )}
        </div>
      )}
      {leCosto.length > 0 && (
        <p className="mt-2 text-[13.5px] text-text-secondary">
          <strong className="text-text">Le costó:</strong> {leCosto.join(", ")}.
        </p>
      )}

      {p.items.length > 0 && (
        <ol className="mt-4 grid gap-3">
          {p.items.map((e) => (
            <li key={e.index} className="rounded-card bg-surface-sunken p-4">
              <Ejercicio ejercicio={e} empezada={empezada} />
            </li>
          ))}
        </ol>
      )}
    </Tarjeta>
  );
}

function Ejercicio({ ejercicio: e, empezada }: { ejercicio: EjercicioDelActa; empezada: boolean }) {
  const resultado = RESULTADO[resultadoDe(e)];
  return (
    <div className="grid gap-2 text-[13.5px]">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex flex-wrap items-center gap-2 text-[11.5px] font-bold uppercase tracking-[0.08em] text-text-muted">
          {e.index + 1}. {NOMBRE_DE_CATEGORIA[e.category]} · {NOMBRE_DEL_TIPO[e.type]}
          {e.sourceTerm && (
            <span className="rounded-pill bg-surface px-2 py-0.5 normal-case tracking-normal text-text-secondary">
              {e.sourceTerm}
            </span>
          )}
        </p>
        {empezada && (
          <span className={`inline-flex items-center gap-1 rounded-pill px-2.5 py-1 text-[11.5px] font-bold ${resultado.clase}`}>
            {resultadoDe(e) === "primero" && <Check size={12} strokeWidth={2.6} />}
            {resultadoDe(e) === "saltado" && <SkipForward size={12} strokeWidth={2.4} />}
            {resultado.texto}
          </span>
        )}
      </div>
      <p className="font-semibold text-text">{e.prompt}</p>
      <Material ejercicio={e} />
      {e.firstAnswer && (
        <div className="rounded-base bg-surface px-3 py-2 text-text-secondary">
          <p>
            <span className="font-bold text-text">{e.secondAnswer ? "Primer intento: " : "Respondió: "}</span>
            <span lang="en">{mostrarRespuesta(e.type, e.payload, e.firstAnswer)}</span>
          </p>
          {e.secondAnswer && (
            <p className="mt-0.5 flex items-start gap-1">
              <CornerDownRight size={14} strokeWidth={2} className="mt-0.5 shrink-0" />
              <span>
                <span className="font-bold text-text">Segundo intento: </span>
                <span lang="en">{mostrarRespuesta(e.type, e.payload, e.secondAnswer)}</span>
              </span>
            </p>
          )}
        </div>
      )}
      <p className="text-text-secondary">
        <span className="font-bold text-text">Respuesta esperada: </span>
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
    case "SPOT_ERROR": {
      const p = leerPayload<{ tokens?: string[] }>(e);
      return <p className="text-text-secondary">Toca la palabra que está mal en «{(p.tokens ?? []).join(" ")}»</p>;
    }
    case "BUILD_SENTENCE": {
      const p = leerPayload<{ tiles?: string[]; guide?: string }>(e);
      return (
        <p className="text-text-secondary">
          {p.guide ? `«${p.guide}» · ` : ""}Fichas: {(p.tiles ?? []).join(" / ")}
        </p>
      );
    }
    case "CHOOSE_REPLY": {
      const p = leerPayload<{ from?: string; message?: string; options?: string[] }>(e);
      return (
        <p className="text-text-secondary">
          {p.from ? `${p.from}: ` : ""}«{p.message}» · Opciones: {(p.options ?? []).join(" / ")}
        </p>
      );
    }
    case "LISTEN_CHOOSE": {
      const p = leerPayload<{ say?: string; options?: string[] }>(e);
      return (
        <p className="text-text-secondary">
          Oye «{p.say}» · Opciones: {(p.options ?? []).join(", ")}
        </p>
      );
    }
    case "DICTATION": {
      const p = leerPayload<{ say?: string }>(e);
      return <p className="text-text-secondary">Oye «{p.say}» y lo escribe</p>;
    }
  }
}
