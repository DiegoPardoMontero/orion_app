"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Check, ChevronDown, ChevronRight } from "lucide-react";
import { apiFetch } from "@/lib/api/fetch";
import type { Diagnostico } from "@/lib/api/diagnostico";
import type { ProfessorCard } from "@/lib/api/types";
import { Avatar } from "@/components/Avatar";
import { Cargando } from "@/components/estados";
import { Meissa } from "@/components/Meissa";
import { precioCop } from "@/lib/format";

/**
 * El resultado: lo que contaste, dónde estás y con quién seguir. En una pantalla corta.
 *
 * <p><strong>Nadie se va con las manos vacías</strong> (Pardo, 22/09/2026). Con número, en español
 * o demasiado corta, la conversación termina igual: con un resumen de lo que contó, una etiqueta de
 * punto de partida y tres profesores. Antes, la rama en español decía «esta vez no te ponemos
 * número» y a veces «todavía no tenemos tres para ti», que es cerrarle la puerta a quien acaba de
 * atreverse a hablar.
 *
 * <p><strong>Se celebra el esfuerzo, nunca cómo habló.</strong> «Hablaste dos minutos en inglés»
 * es un hecho; «hablas muy bien» sería una evaluación, y la regla de marca del diagnóstico la
 * prohíbe (ver {@code TextosDelDiagnosticoTest}).
 *
 * <p>El número va con su etiqueta y nunca como letra del MCER: mide confianza al hablar, no
 * competencia, y una letra afirmaría algo que no medimos. Meissa cierra con los ojos cerrados de
 * gusto y un destello; no celebra.
 */
export function Resultado({
  diagnostico,
  sinCuenta = false,
}: {
  diagnostico: Diagnostico;
  /** Hecho sin cuenta: se ofrece guardarlo, sin obligar a nada. */
  sinCuenta?: boolean;
}) {
  const sinNumero = diagnostico.mode === "FROM_ZERO" || diagnostico.score == null;

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-5 lg:max-w-5xl lg:py-10">
      <div className="flex items-center justify-between">
        <span className="inline-flex items-center gap-1.5 rounded-pill bg-[#DEF3E7] px-3 py-1 text-[12.5px] font-bold text-[#2E6B4A]">
          <Check size={14} strokeWidth={2.6} />
          Diagnóstico listo
        </span>
        {diagnostico.durationSeconds != null && (
          <span className="text-[14px] font-semibold tabular-nums text-text-secondary">
            {reloj(diagnostico.durationSeconds)}
          </span>
        )}
      </div>

      <div className="mt-4 grid gap-6 lg:grid-cols-[1fr_1.05fr] lg:items-start lg:gap-10">
        <div>
          <div className="flex items-center gap-3">
            <Meissa estado="cierre" decorativo className="h-[120px] w-auto shrink-0" />
            <p className="rounded-[22px_22px_22px_6px] bg-surface-raised px-4 py-3 text-[15px] font-medium leading-snug shadow-sm">
              Gracias por hablar conmigo. Ya te conozco un poco: esto es lo que veo.
            </p>
          </div>

          <h1 className="mt-5 font-display text-[22px] font-bold leading-tight lg:text-[26px]">
            {animo(diagnostico)}
          </h1>
          <p className="mt-2 text-[14.5px] leading-relaxed text-text-secondary">
            En Orión vas a tener muchas conversaciones como esta, con un profesor que te acompaña en
            cada una. Cada vez te van a salir con más calma.
          </p>

          <section className="mt-5 rounded-[24px] bg-[#33203B] p-6 text-[#FFF6EE]">
            <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-accent-lavender">
              Tu punto de partida
            </p>
            {sinNumero ? (
              <p className="mt-2 font-display text-[34px] font-extrabold leading-none">
                {diagnostico.label}
              </p>
            ) : (
              <p className="mt-2 flex items-baseline gap-3">
                <span className="font-display text-[56px] font-extrabold leading-none tabular-nums">
                  {diagnostico.score}
                </span>
                <span className="whitespace-nowrap text-[15px] font-semibold">{diagnostico.label}</span>
                <span className="sr-only">Confidence Score {diagnostico.score} sobre 100.</span>
              </p>
            )}
            {diagnostico.summary && (
              <p className="mt-4 text-[14px] leading-relaxed text-[#EFE9F9]">{diagnostico.summary}</p>
            )}
          </section>
        </div>

        <div>
          <Profesores diagnostico={diagnostico} />
          {sinCuenta && <Guardarlo />}
          <Detalle diagnostico={diagnostico} />
        </div>
      </div>
    </main>
  );
}

/** El esfuerzo, dicho como hecho. En la rama en español no se dice «en inglés»: no sería cierto. */
function animo(diagnostico: Diagnostico) {
  const minutos = minutosEnPalabras(diagnostico.durationSeconds);
  if (diagnostico.mode === "FROM_ZERO") {
    return `Conversaste ${minutos} con Meissa. Ese es el primer paso, y el más difícil.`;
  }
  return `Hablaste ${minutos} en inglés con alguien que no conocías. Eso ya es un gran paso.`;
}

function minutosEnPalabras(segundos: number | null) {
  const m = Math.max(1, Math.round((segundos ?? 120) / 60));
  const palabras = ["", "un minuto", "dos minutos", "tres minutos", "cuatro minutos", "cinco minutos"];
  return palabras[m] ?? `${m} minutos`;
}

function reloj(segundos: number) {
  const m = Math.floor(segundos / 60);
  const s = segundos % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

/** Los tres profesores, compactos: foto, nombre, por qué, precio. Toda la fila lleva a su agenda. */
function Profesores({ diagnostico }: { diagnostico: Diagnostico }) {
  const ids = diagnostico.recommendations.map((r) => r.professorId);

  const fichas = useQuery({
    queryKey: ["professors", "recomendados", ids],
    queryFn: async () =>
      Promise.all(ids.map((id) => apiFetch<ProfessorCard>(`/api/v1/professors/${id}`))),
    enabled: ids.length > 0,
  });

  // Solo si la plataforma entera no tiene profesores del idioma: el servicio completa hasta tres.
  if (ids.length === 0) {
    return (
      <section>
        <h2 className="font-display text-[19px] font-bold">Tus profesores</h2>
        <Link
          href="/profesores"
          className="mt-3 inline-flex h-11 items-center rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Conoce a los profesores de Orión
        </Link>
      </section>
    );
  }

  return (
    <section>
      <h2 className="font-display text-[19px] font-bold">Tres profesores para ti</h2>
      <p className="mt-0.5 text-[13.5px] text-text-secondary">
        Elige uno y reserva tu primera clase.
      </p>

      {fichas.isPending ? (
        <div className="mt-3">
          <Cargando filas={3} />
        </div>
      ) : (
        <ul className="mt-3 grid gap-2.5">
          {diagnostico.recommendations.map((rec) => {
            const ficha = (fichas.data ?? []).find((f) => f?.id === rec.professorId);
            if (!ficha) return null;
            return (
              <li key={rec.professorId}>
                <Link
                  href={`/profesores/${rec.professorId}`}
                  className="flex items-center gap-3 rounded-card bg-surface-raised p-3.5 shadow-sm transition-shadow hover:shadow-md focus-visible:shadow-focus"
                >
                  <Avatar nombre={ficha.fullName ?? ""} fotoUrl={ficha.photoUrl} size="md" />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-baseline justify-between gap-2">
                      <span className="truncate font-display text-[15.5px] font-bold">
                        {ficha.fullName}
                      </span>
                      {ficha.hourlyRateCop != null && (
                        <span className="shrink-0 text-[12.5px] text-text-muted">
                          {precioCop(ficha.hourlyRateCop)}
                        </span>
                      )}
                    </span>
                    <span className="mt-0.5 line-clamp-2 block text-[13px] leading-snug text-text-secondary">
                      {rec.reasonText}
                    </span>
                  </span>
                  <ChevronRight size={18} strokeWidth={2.2} className="shrink-0 text-primary-strong" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

/**
 * «¿Te lo guardamos?», para quien lo hizo sin cuenta. Opcional y después del resultado, nunca antes:
 * esconder el resultado detrás de un registro se sentiría como una trampa. Al crear la cuenta o
 * entrar, el diagnóstico pasa a ella solo (el backend lo reclama con la cookie de este dispositivo).
 */
function Guardarlo() {
  return (
    <section className="mt-4 rounded-card border border-border bg-surface-raised p-4">
      <p className="font-display text-[16px] font-bold">¿Te lo guardamos?</p>
      <p className="mt-1 text-[13.5px] leading-relaxed text-text-secondary">
        Crea tu cuenta y tu resultado queda en ella. También te hará falta para reservar.
      </p>
      <div className="mt-3 grid grid-cols-2 gap-2">
        <Link
          href="/registro?desde=diagnostico"
          className="inline-flex h-11 items-center justify-center rounded-pill bg-primary px-3 text-[14px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Crear cuenta
        </Link>
        <Link
          href="/login?desde=diagnostico"
          className="inline-flex h-11 items-center justify-center rounded-pill border-[1.5px] border-border px-3 text-[14px] font-bold text-text hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          Ya tengo cuenta
        </Link>
      </div>
    </section>
  );
}

/** Lo que se notó en la conversación, plegado: está para quien lo busque, no alarga la pantalla. */
function Detalle({ diagnostico }: { diagnostico: Diagnostico }) {
  if (diagnostico.observations.length === 0) return null;
  return (
    <details className="group mt-4">
      <summary className="flex h-11 cursor-pointer list-none items-center justify-center gap-1.5 rounded-pill text-[14px] font-bold text-text-secondary hover:bg-surface-sunken focus-visible:shadow-focus">
        Ver el detalle de mi diagnóstico
        <ChevronDown size={16} strokeWidth={2.2} className="transition-transform group-open:rotate-180" />
      </summary>
      <ul className="mt-2 grid gap-2">
        {diagnostico.observations.map((obs) => (
          <li
            key={obs}
            className="rounded-card border-l-[3px] border-accent-peach bg-surface-raised p-3.5 text-[13.5px] leading-relaxed text-text-secondary"
          >
            {TEXTO_OBSERVACION[obs] ?? obs}
          </li>
        ))}
      </ul>
    </details>
  );
}

/** Las observaciones, dichas como hallazgo y nunca como corrección. */
const TEXTO_OBSERVACION: Record<string, string> = {
  STEADY_START: "Arrancas sin titubear: dices la primera frase y sigues.",
  LONG_ANSWERS_WHEN_COMFORTABLE:
    "Cuando el tema te resulta cómodo te alargas. Ahí tienes más idioma del que usas.",
  HOLDS_UNDER_PRESSURE:
    "No te encogiste cuando la pregunta pidió construir una idea más larga.",
  SLOW_START: "Te tomas unos segundos antes de empezar a hablar.",
  ABANDONS_CLAUSES:
    "Empiezas frases y las sueltas a mitad. Suele ser prisa, no falta de palabras.",
  SHORT_ANSWERS: "Respondes corto aunque se note que entendiste la pregunta.",
  RETREATS_TO_NATIVE: "Te devuelves al español cuando el terreno se pone difícil.",
  FILLER_HEAVY: "Usas mucho relleno mientras buscas la palabra. Es esfuerzo, no error.",
};
