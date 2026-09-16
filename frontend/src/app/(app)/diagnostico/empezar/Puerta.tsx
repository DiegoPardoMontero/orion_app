"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Check, Mic, ShieldCheck } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import type { GoalResponse } from "@/lib/api/types";
import { AvisoError } from "@/components/estados";
import { Boton } from "@/components/ui";
import { Rigel } from "@/components/Rigel";

/**
 * La puerta de entrada: para qué quieres el idioma, y el permiso para usar tu voz.
 *
 * <p>El objetivo se pregunta aquí y no al final porque alimenta las <strong>recomendaciones</strong>,
 * no el puntaje: para qué quieres el inglés no te hace hablarlo mejor ni peor, pero sí cambia con
 * quién te conviene empezar.
 *
 * <p>El consentimiento va en texto propio y separado de los términos. La voz es un dato biométrico
 * y su autorización tiene que ser previa, expresa y específica; meterla dentro de «acepto los
 * términos» la viciaría. La casilla <strong>nace desmarcada</strong> y sin ella no se avanza — una
 * casilla premarcada no es un consentimiento, es una suposición.
 */
export function Puerta({ onListo }: { onListo: (objetivos: string[]) => void }) {
  const [objetivos, setObjetivos] = useState<string[]>([]);
  const [acepta, setAcepta] = useState(false);

  const catalogo = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: 5 * 60_000,
  });

  const consentir = useMutation({
    mutationFn: () => apiFetch("/api/v1/me/voice-consent", { method: "POST", body: {} }),
    onSuccess: () => onListo(objetivos),
  });

  const alternar = (code: string) =>
    setObjetivos((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code],
    );

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-6 lg:py-10">
      <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">
        Diagnóstico de confianza
      </p>
      <h1 className="mt-2 font-display text-h1 font-bold">Antes de empezar.</h1>
      <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">
        Dos preguntas rápidas y ya. No hay examen ni nota.
      </p>

      <section className="mt-7">
        <h2 className="text-[15px] font-bold text-text">¿Para qué quieres el inglés?</h2>
        <p className="mt-0.5 text-[13px] text-text-secondary">
          Elige lo que te sirva. Esto no cambia tu resultado: cambia con quién te conviene empezar.
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          {(catalogo.data ?? []).map((meta) => {
            const activo = objetivos.includes(meta.code ?? "");
            return (
              <button
                key={meta.code}
                type="button"
                aria-pressed={activo}
                onClick={() => meta.code && alternar(meta.code)}
                className={`min-h-11 rounded-pill px-4 text-[13.5px] font-semibold transition-colors focus-visible:shadow-focus ${
                  activo
                    ? "bg-primary text-on-primary"
                    : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                }`}
              >
                {meta.nameEs}
              </button>
            );
          })}
        </div>
      </section>

      <section className="mt-8 rounded-card bg-accent-lavender-soft p-5">
        <p className="flex items-center gap-2 text-[13.5px] font-bold text-info">
          <Mic size={16} strokeWidth={2.2} />
          Tu voz
        </p>
        <p className="mt-2 text-[13.5px] leading-relaxed text-text-secondary">
          Vamos a procesar tu voz para medir cómo te desenvuelves.{" "}
          <strong className="text-text">No guardamos el audio.</strong> Guardamos la transcripción y
          las señales durante un año, y puedes pedir que las borremos cuando quieras.
        </p>

        <label className="mt-4 flex cursor-pointer items-start gap-3 rounded-base bg-surface-raised p-3.5">
          <input
            type="checkbox"
            checked={acepta}
            onChange={(e) => setAcepta(e.target.checked)}
            className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--color-primary)]"
          />
          <span className="text-[13.5px] font-semibold text-text">Entiendo y acepto</span>
        </label>
      </section>

      {consentir.error instanceof ApiError && (
        <div className="mt-4">
          <AvisoError mensaje={consentir.error.message} />
        </div>
      )}

      <Boton
        variante="primario"
        className="mt-6 w-full"
        disabled={!acepta || consentir.isPending}
        onClick={() => consentir.mutate()}
      >
        {consentir.isPending ? "Un momento…" : "Empezar la conversación"}
      </Boton>

      <p className="mt-4 flex items-start gap-2 text-[12.5px] leading-relaxed text-text-muted">
        <ShieldCheck size={15} strokeWidth={2} className="mt-0.5 shrink-0" />
        <span>
          Puedes retirar esta autorización cuando quieras desde tu perfil. Al hacerlo borramos lo que
          hayamos guardado.
        </span>
      </p>

      <div className="mt-7 flex items-center gap-3 rounded-card bg-accent-peach-soft p-4">
        <Rigel pose="espera" decorativo className="h-14 w-auto shrink-0" />
        <p className="text-[13px] leading-relaxed text-[#8a5a33]">
          <Check size={13} strokeWidth={2.4} className="mr-1 inline align-[-2px]" />
          Nadie te va a corregir mientras hablas. De eso se trata.
        </p>
      </div>
    </main>
  );
}
