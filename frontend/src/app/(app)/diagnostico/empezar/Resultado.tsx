"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Sparkles } from "lucide-react";
import { apiFetch } from "@/lib/api/fetch";
import type { Diagnostico } from "@/lib/api/diagnostico";
import type { ProfessorCard } from "@/lib/api/types";
import { Avatar } from "@/components/Avatar";
import { Cargando } from "@/components/estados";
import { Rigel } from "@/components/Rigel";
import { precioCop } from "@/lib/format";

/**
 * El resultado: dónde estás, qué se notó, y con quién seguir.
 *
 * <p>El número aparece pequeño y acompañado, nunca desnudo. Un 62 sin nada al lado es una nota, y
 * esto no es un examen — por eso va dentro de una constelación de diez estrellas, que es el lenguaje
 * que la plataforma ya usa para hablar de progreso.
 *
 * <p>El coral se reserva para la acción —ver la agenda de un profesor—. El resultado vive en
 * durazno y lavanda, que es el territorio del avance. Un puntaje pintado del color del botón
 * convierte una medición en una venta.
 *
 * <p><strong>Modo FROM_ZERO: sin constelación y sin número.</strong> No es una limitación técnica,
 * es la decisión: mostrarle un número bajo a alguien que está empezando desde cero es exactamente
 * lo que Orión no hace.
 */
export function Resultado({ diagnostico }: { diagnostico: Diagnostico }) {
  const desdeCero = diagnostico.mode === "FROM_ZERO" || diagnostico.score == null;

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-6 lg:max-w-4xl lg:py-10">
      {desdeCero ? <Bienvenida /> : <PuntoDePartida diagnostico={diagnostico} />}

      {diagnostico.observations.length > 0 && (
        <section className="mt-9">
          <h2 className="font-display text-[19px] font-bold">Lo que notamos</h2>
          <ul className="mt-3 grid gap-2.5">
            {diagnostico.observations.map((obs) => (
              <li
                key={obs}
                className="rounded-card border-l-[3px] border-accent-peach bg-surface-raised p-4 text-[14px] leading-relaxed text-text-secondary shadow-sm"
              >
                {TEXTO_OBSERVACION[obs] ?? obs}
              </li>
            ))}
          </ul>
        </section>
      )}

      <Profesores diagnostico={diagnostico} />

      <p className="mt-10 text-center text-[13px] text-text-muted">
        Tu resultado queda guardado en tu perfil. También te lo enviamos por correo.
      </p>
    </main>
  );
}

function PuntoDePartida({ diagnostico }: { diagnostico: Diagnostico }) {
  const puntaje = diagnostico.score ?? 0;
  const encendidas = Math.max(1, Math.round(puntaje / 10));

  return (
    <section className="rounded-card bg-[linear-gradient(150deg,#2E1E4E_0%,#4A2E63_100%)] p-7 text-text-on-night">
      <p className="text-[12px] font-bold uppercase tracking-[0.12em] text-accent-peach">
        Tu punto de partida
      </p>

      {/* Diez estrellas, no un número desnudo: el mismo lenguaje con el que la plataforma ya
          cuenta el progreso. El número está, pequeño y al lado. */}
      <div className="mt-4 flex items-end gap-3">
        <span className="flex gap-1" aria-hidden="true">
          {Array.from({ length: 10 }, (_, i) => (
            <Estrella key={i} encendida={i < encendidas} />
          ))}
        </span>
        <span className="font-display text-[22px] font-bold leading-none text-accent-peach">
          {puntaje}
        </span>
      </div>
      <p className="sr-only">Tu Confidence Score es {puntaje} sobre 100.</p>

      {diagnostico.summary && (
        <p className="mt-5 max-w-[52ch] text-[15px] leading-relaxed text-text-on-night/90">
          {diagnostico.summary}
        </p>
      )}
    </section>
  );
}

function Bienvenida() {
  return (
    <section className="flex items-center gap-4 rounded-card bg-accent-peach-soft p-7">
      <Rigel pose="animo" decorativo className="h-24 w-auto shrink-0" />
      <div>
        <h1 className="font-display text-h2 font-bold text-[#8a5a33]">
          Estás empezando, y ese es un buen lugar para empezar.
        </h1>
        <p className="mt-2 text-[14px] leading-relaxed text-[#8a5a33]">
          Esta vez no te ponemos número. Hablar un idioma que no dominas cuesta, y medirte el primer
          día no te diría nada útil. Empieza con alguien que enseñe desde cero y vuelve cuando
          quieras.
        </p>
      </div>
    </section>
  );
}

function Estrella({ encendida }: { encendida: boolean }) {
  return (
    <svg viewBox="0 0 24 24" width={17} height={17} aria-hidden="true">
      <polygon
        points="12,2 15,9 22,12 15,15 12,22 9,15 2,12 9,9"
        fill={encendida ? "#FFC189" : "none"}
        stroke={encendida ? "#FFC189" : "rgba(255,246,238,.35)"}
        strokeWidth={1.5}
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Las tres recomendaciones, con la tarjeta de profesor de siempre y la razón debajo. */
function Profesores({ diagnostico }: { diagnostico: Diagnostico }) {
  const ids = diagnostico.recommendations.map((r) => r.professorId);

  const fichas = useQuery({
    queryKey: ["professors", "recomendados", ids],
    queryFn: async () =>
      Promise.all(ids.map((id) => apiFetch<ProfessorCard>(`/api/v1/professors/${id}`))),
    enabled: ids.length > 0,
  });

  if (ids.length === 0) {
    return (
      <section className="mt-9 rounded-card border border-border bg-surface-raised p-6">
        <h2 className="font-display text-[19px] font-bold">Todavía no tenemos tres para ti</h2>
        <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
          No queremos recomendarte a alguien que no encaje solo por llenar la lista. Mira el
          directorio completo y elige tú.
        </p>
        <Link
          href="/profesores"
          className="mt-4 inline-flex h-11 items-center rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Ver profesores
        </Link>
      </section>
    );
  }

  if (fichas.isPending) return <div className="mt-9"><Cargando filas={3} /></div>;

  return (
    <section className="mt-9">
      <h2 className="flex items-center gap-2 font-display text-[19px] font-bold">
        <Sparkles size={18} strokeWidth={2} className="text-accent-lavender" />
        Tres profesores para ti
      </h2>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Elegidos por lo que contaste, no por un catálogo genérico.
      </p>

      <ul className="mt-4 grid gap-3 lg:grid-cols-3">
        {diagnostico.recommendations.map((rec) => {
          const ficha = (fichas.data ?? []).find((f) => f?.id === rec.professorId);
          if (!ficha) return null;
          return (
            <li
              key={rec.professorId}
              className="flex flex-col rounded-card bg-surface-raised p-5 shadow-sm"
            >
              <div className="flex items-center gap-3">
                <Avatar nombre={ficha.fullName ?? ""} fotoUrl={ficha.photoUrl} size="md" />
                <div className="min-w-0">
                  <p className="truncate font-display text-[16px] font-bold">{ficha.fullName}</p>
                  {ficha.hourlyRateCop && (
                    <p className="text-[12.5px] text-text-muted">
                      {precioCop(ficha.hourlyRateCop)} por clase
                    </p>
                  )}
                </div>
              </div>

              <p className="mt-3 flex-1 rounded-base bg-accent-lavender-soft px-3 py-2.5 text-[13px] leading-relaxed text-info">
                {rec.reasonText}
              </p>

              <Link
                href={`/profesores/${rec.professorId}`}
                className="mt-4 inline-flex h-11 items-center justify-center gap-1.5 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
              >
                Ver agenda
                <ArrowRight size={15} strokeWidth={2.2} />
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
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
