"use client";

import { useQuery } from "@tanstack/react-query";
import { Check, Eye, Flame } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { SelloDeLogro } from "@/components/gamificacion/SelloDeLogro";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import type { Engagement, Logro } from "@/lib/gamificacion";
import {
  estrellaDe,
  estrellasDe,
  leerPayload,
  nombreDelProfe,
  PUNTOS_CONSTELACION_PERFECTA,
  PUNTOS_POR_PRACTICA,
  tituloDelSet,
  unirFichas,
  type Ejercicio,
  type SetDePractica,
} from "@/lib/practica";
import { Constelacion, formaDe } from "./Constelacion";

/**
 * El cierre del set (handoff `design_handoff_orion_practica`, §10.5): la constelación se dibuja en
 * el amanecer —el único lugar de la práctica donde va el degradado—, y después los puntos que se
 * cuentan solos, el logro nuevo si lo hubo, lo que logró, lo que conviene repasar y la racha.
 *
 * <p>Nunca «3 de 5» ni un porcentaje: la práctica no es un examen y la pantalla no puede insinuar
 * que lo es. Y ningún número inventado: los puntos son los que el servidor da por el set, el bono y
 * cada logro que se encendió al terminarlo.
 */
export function Cierre({ set }: { set: SetDePractica }) {
  const logros = useQuery({
    queryKey: ["me", "achievements"],
    queryFn: () => apiFetch<Logro[]>("/api/v1/me/achievements"),
    staleTime: 30_000,
  });
  const resumen = useQuery({
    queryKey: ["me", "engagement"],
    queryFn: () => apiFetch<Engagement>("/api/v1/me/engagement"),
    staleTime: 30_000,
  });

  const items = [...set.items].sort((a, b) => a.index - b.index);
  const titulo = tituloDelSet(set) ?? "Tu práctica";
  const profe = nombreDelProfe(set, true);
  const nuevos = logrosDelCierre(logros.data ?? [], set.completedAt);
  const lineas = [
    { etiqueta: "Completaste el set", puntos: PUNTOS_POR_PRACTICA },
    ...(set.perfect ? [{ etiqueta: "Constelación perfecta", puntos: PUNTOS_CONSTELACION_PERFECTA }] : []),
    ...nuevos.map((l) => ({ etiqueta: `Logro · ${l.name}`, puntos: l.points })),
  ];
  const total = lineas.reduce((s, l) => s + l.puntos, 0);
  const logrado = items.map(loQueLogro).filter((t): t is string => t !== null);
  const repasar = Array.from(new Set(items.filter((i) => ["segundo", "mostrada"].includes(estrellaDe(i))).map(terminoDe)));
  const semanas = resumen.data?.currentStreakWeeks ?? 0;

  const hero = (
    <div className="gradient-dawn flex flex-col items-center gap-3 px-5 pt-7 pb-6 text-center text-crema lg:h-full lg:justify-center lg:gap-[18px] lg:rounded-[28px] lg:p-8">
      <Constelacion
        estados={estrellasDe(items)}
        forma={formaDe(set.id)}
        ancho={440}
        r={11}
        lineas
        dibujar
        fondo="noche"
        perfecta={set.perfect}
        etiqueta={`Constelación ${set.perfect ? "perfecta" : "completa"}: ${items.length} estrellas unidas.`}
        className="w-[300px] lg:w-[440px]"
      />
      <Rigel pose="celebracion" decorativo className="h-auto w-[110px] lg:w-[160px]" />
      <h1 className="m-0 font-display text-[32px] leading-[1.05] font-extrabold lg:text-[48px] lg:leading-none">
        {set.perfect ? "Constelación perfecta" : "Constelación completa"}
      </h1>
      <span className="text-[15px] lg:text-[17px]">{titulo} ya brilla en tu cielo.</span>
    </div>
  );

  return (
    <div className="practica flex min-h-dvh flex-col bg-crema text-ink lg:grid lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:gap-7 lg:px-10 lg:py-8">
      {hero}
      <div className="flex flex-col gap-4 p-5 lg:gap-3.5 lg:p-0">
        <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[repeat(auto-fit,minmax(0,1fr))] lg:gap-3.5">
          <Puntos lineas={lineas} total={total} />
          {nuevos.map((l) => (
            <div key={l.code} className="flex items-center gap-3.5 rounded-tarjeta bg-white px-[18px] py-4">
              <SelloDeLogro logro={l} size={76} />
              <div className="flex flex-1 flex-col gap-0.5">
                <span className="text-[12px] font-extrabold tracking-[.06em] text-durazno-ink uppercase">Logro nuevo</span>
                <strong className="font-display text-[18px]">{l.name}</strong>
                <span className="text-[13px] text-ink-2">+{l.points} puntos</span>
              </div>
            </div>
          ))}
        </div>

        <div className="flex flex-col gap-2 lg:rounded-tarjeta lg:bg-white lg:px-5 lg:py-[18px]">
          {logrado.length > 0 && (
            <>
              <h2 className="m-0 text-[16px] font-extrabold">Lo que lograste</h2>
              {logrado.map((l) => (
                <div key={l} className="flex items-start gap-2.5 text-[15px] leading-[1.45]">
                  <Check size={18} strokeWidth={2} className="mt-0.5 shrink-0 text-ok-icon" aria-hidden />
                  <span>{l}</span>
                </div>
              ))}
            </>
          )}
          {repasar.length > 0 && (
            <>
              <h2 className="m-0 mt-2 text-[16px] font-extrabold lg:mt-2">Para repasar</h2>
              <div className="flex flex-wrap gap-2">
                {repasar.map((r) => (
                  <span key={r} lang="en" className="inline-flex h-[34px] items-center rounded-pill bg-durazno-soft px-3.5 text-[14px] font-bold text-[#6B3E1A]">
                    {r}
                  </span>
                ))}
              </div>
            </>
          )}
        </div>

        {semanas > 0 && (
          <div className="flex items-center gap-3 rounded-tarjeta bg-white px-[18px] py-3.5">
            <Flame size={22} strokeWidth={1.75} className="shrink-0 text-durazno-ink" aria-hidden />
            <span className="text-[15px] leading-[1.4]">
              <strong>
                Tu racha sigue: {semanas} {semanas === 1 ? "semana" : "semanas"}.
              </strong>{" "}
              Practicaste esta semana también.
            </span>
          </div>
        )}
        <div className="flex items-center gap-2.5 text-[14px] text-ink-2">
          <Eye size={18} strokeWidth={1.75} className="shrink-0" aria-hidden />
          {profe} ya puede ver cómo te fue.
        </div>

        <div className="min-h-0 flex-1" />
        <div className="flex flex-col gap-2 lg:flex-row lg:flex-wrap lg:gap-3">
          <Link
            href="/cuenta?seccion=resumen"
            className="flex h-14 items-center justify-center rounded-pill bg-coral px-8 text-[16px] font-bold whitespace-nowrap text-crema shadow-cta transition-colors hover:bg-coral-hover"
          >
            Volver a mi perfil
          </Link>
          {set.bookingId && (
            <Link
              href={`/mis-clases/${set.bookingId}/acta`}
              className="flex h-[52px] items-center justify-center rounded-pill border-[1.5px] border-ink px-6 text-[16px] font-bold whitespace-nowrap text-ink transition-colors hover:bg-arena lg:h-14"
            >
              Ver el resumen de la clase
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Los logros que se encendieron con este set: los que se consiguieron en el minuto siguiente a
 * cerrarlo. Se miran por la hora y no se guardan aparte, porque así también se ven si la persona
 * recarga el cierre.
 */
export function logrosDelCierre(logros: Logro[], completadoEn: string | null): Logro[] {
  if (!completadoEn) return [];
  const fin = new Date(completadoEn).getTime();
  return logros.filter((l) => {
    if (!l.unlocked || !l.unlockedAt) return false;
    const t = new Date(l.unlockedAt).getTime();
    return t >= fin - 5_000 && t <= fin + 60_000;
  });
}


/**
 * Los puntos (§9.8): solo las líneas que ocurrieron, y el total que se cuenta de cero al final en
 * 600 ms. El lector de pantalla oye el total y el desglose de una vez, no cada número.
 */
function Puntos({ lineas, total }: { lineas: { etiqueta: string; puntos: number }[]; total: number }) {
  const [mostrado, setMostrado] = useState(0);
  useEffect(() => {
    const quieto = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    let raf = 0;
    const inicio = performance.now();
    const paso = (ahora: number) => {
      const t = quieto ? 1 : Math.min(1, (ahora - inicio) / 600);
      setMostrado(Math.round(total * (1 - Math.pow(1 - t, 3))));
      if (t < 1) raf = requestAnimationFrame(paso);
    };
    raf = requestAnimationFrame(paso);
    return () => cancelAnimationFrame(raf);
  }, [total]);

  const aria = `Sumaste ${total} puntos: ${lineas.map((l) => `${l.puntos} por ${l.etiqueta.toLowerCase()}`).join(", ")}`;
  return (
    <div aria-label={aria} role="group" className="flex flex-col gap-2.5 rounded-tarjeta bg-white px-5 py-[18px] lg:gap-2">
      {lineas.map((l, i) => (
        <div
          key={l.etiqueta}
          aria-hidden
          className={`flex items-center justify-between text-[15px] lg:text-[14px] ${i > 0 ? "pr-bono" : ""}`}
          style={i > 0 ? { animationDelay: `${600 + (i - 1) * 700}ms` } : undefined}
        >
          <span className="font-semibold text-ink-2">{l.etiqueta}</span>
          <span className="font-display text-[20px] font-extrabold text-durazno-ink lg:text-[18px]">+{l.puntos}</span>
        </div>
      ))}
      <div aria-hidden className="flex items-baseline justify-between border-t border-line-soft pt-2.5 lg:pt-2">
        <span className="font-bold">Sumaste</span>
        <span className="font-display text-[34px] font-extrabold tabular-nums lg:text-[32px]">+{mostrado}</span>
      </div>
    </div>
  );
}

/** «Lo que lograste»: una línea por ejercicio resuelto, dicha como un logro y no como una nota. */
function loQueLogro(i: Ejercicio): string | null {
  const e = estrellaDe(i);
  if (e !== "primero" && e !== "segundo") return null;
  const primera = e === "primero";
  const p = leerPayload<{ terms?: string[]; sentence?: string; tokens?: string[]; term?: string }>(i);
  switch (i.type) {
    case "MATCH_MEANING": {
      const n = p.terms?.length ?? 0;
      const cuantas = ["", "La palabra nueva", "Las dos palabras nuevas", "Las tres palabras nuevas", "Las cuatro palabras nuevas", "Las cinco palabras nuevas"][n] ?? "Las palabras nuevas";
      return primera ? `${cuantas}, a la primera.` : `${cuantas}, unidas.`;
    }
    case "FILL_BLANK":
      return `Completaste la frase con «${i.answer ?? i.expected ?? ""}».`;
    case "FIX_SENTENCE":
      return primera ? `Corregiste «${p.sentence}» sin ayuda.` : `Corregiste «${p.sentence}».`;
    case "SPOT_ERROR":
      return `Cazaste el error de «${unirFichas(p.tokens ?? []).replace(/[.!?]+$/, "")}».`;
    case "BUILD_SENTENCE":
      return i.expected ? `Armaste «${unirFichas(JSON.parse(i.expected) as string[])}».` : "Armaste la frase en inglés.";
    case "ORDER_DIALOGUE":
      return "Pusiste la conversación en orden.";
    case "CHOOSE_REPLY":
      return "Respondiste bien en el chat.";
    case "LISTEN_CHOOSE":
      return primera ? "Entendiste a Meissa a la primera." : "Entendiste a Meissa.";
    case "DICTATION":
      return "Escribiste lo que dijo Meissa.";
    case "WRITE_SENTENCE":
      return `Escribiste tu propia frase con ${p.term}.`;
  }
}

/** «Para repasar»: el término del ejercicio que costó, o lo que se practicaba cuando no hay uno. */
function terminoDe(i: Ejercicio): string {
  const p = leerPayload<{ term?: string; say?: string }>(i);
  if (p.term) return p.term;
  if (i.type === "LISTEN_CHOOSE" && p.say) return p.say;
  if (i.type === "FILL_BLANK" && i.expected) return i.expected;
  if (i.type === "DICTATION") return "escribir lo que oyes";
  if (i.type === "SPOT_ERROR") return "encontrar el error";
  if (i.type === "BUILD_SENTENCE") return "armar frases";
  if (i.type === "CHOOSE_REPLY") return "responder en una conversación";
  if (i.type === "FIX_SENTENCE") return "la corrección de frases";
  if (i.type === "ORDER_DIALOGUE") return "el orden de un diálogo";
  if (i.type === "MATCH_MEANING") return "los significados";
  return "completar frases";
}
