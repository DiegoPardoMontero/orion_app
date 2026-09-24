"use client";

import { useQuery } from "@tanstack/react-query";
import { Check, Clock, Eye, Loader, Minus, PlayCircle, RotateCcw, SkipForward, type LucideIcon } from "lucide-react";
import { apiFetch } from "@/lib/api/fetch";
import {
  diaCorto,
  leerPayload,
  mostrarEsperada,
  mostrarRespuesta,
  NOMBRE_DE_CATEGORIA,
  NOMBRE_DEL_TIPO,
  numeroEnPalabras,
  primerNombre,
  resultadoDe,
  unirFichas,
  type EjercicioDelActa,
  type EstrellaDelEjercicio,
  type PracticaDelActa,
  type ResultadoDelEjercicio,
} from "@/lib/practica";
import { Constelacion, formaDe } from "./Constelacion";
import { CATEGORIA } from "./piezas";

/**
 * Cómo le fue a tu estudiante (handoff `design_handoff_orion_practica`, §10.11 y §9.10): va debajo
 * del acta publicada. Se lee de un vistazo —verde al primer intento, ámbar al segundo, neutro lo
 * que se le mostró o saltó; nunca rojo— y cada ejercicio trae lo que vio, lo que respondió en cada
 * intento y lo que se esperaba. El estudiante lo sabe desde el inicio de su práctica.
 */

type Chip = { texto: string; fondo: string; tinta: string; borde?: string; I: LucideIcon };

/** Verde el primer intento, ámbar el segundo, neutro lo mostrado o saltado. Nunca rojo. */
const RESULTADO: Record<ResultadoDelEjercicio, Chip & { cabecera: string }> = {
  primero: { texto: "Al primer intento", fondo: "#DEF3E7", tinta: "#1F5238", I: Check, cabecera: "#F1FAF4" },
  segundo: { texto: "Al segundo intento", fondo: "#FFEBC7", tinta: "#6B440A", I: RotateCcw, cabecera: "#FFF8EA" },
  mostrada: { texto: "Se le mostró", fondo: "#FFFFFF", tinta: "#4A3A75", borde: "1.5px solid #D5CAF0", I: Eye, cabecera: "#F8F5FC" },
  saltado: { texto: "Lo saltó", fondo: "#FFFFFF", tinta: "#33203B", borde: "1.5px solid #E3D6CA", I: SkipForward, cabecera: "#FBF7F3" },
  sinHacer: { texto: "Sin hacer", fondo: "#FFFFFF", tinta: "#5E4E6B", borde: "1.5px dashed #C9B8A8", I: Minus, cabecera: "#FBF7F3" },
};

const ESTRELLA: Record<ResultadoDelEjercicio, EstrellaDelEjercicio> = {
  primero: "primero",
  segundo: "segundo",
  mostrada: "mostrada",
  saltado: "saltada",
  sinHacer: "off",
};

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
  const items = [...p.items].sort((a, b) => a.index - b.index);
  const resultados = items.map(resultadoDe);
  const siguiente = p.status === "IN_PROGRESS" ? resultados.indexOf("sinHacer") : -1;
  const estrellas = resultados.map((r, i) => (i === siguiente ? "actual" : ESTRELLA[r]));
  const completa = p.status === "COMPLETED";
  const empezada = p.status === "IN_PROGRESS" || completa || (p.status === "EXPIRED" && items.some((e) => e.attempts > 0 || e.skipped));
  const leCosto = Array.from(
    new Set(
      items
        .filter((e) => ["segundo", "mostrada"].includes(resultadoDe(e)))
        .map((e) => {
          const t = e.sourceTerm ?? NOMBRE_DEL_TIPO[e.type];
          return e.category === "ESCUCHA" ? `${t} (al oído)` : t;
        }),
    ),
  );

  const { chip, cuerpo } = encabezado(p, nombre, resultados);

  return (
    <section aria-labelledby="como-le-fue" className="practica flex flex-col gap-4">
      <div className="flex flex-col gap-3.5 rounded-tarjeta bg-white p-[18px] lg:px-[26px] lg:py-[22px]">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex flex-col gap-2">
            <h2 id="como-le-fue" className="m-0 font-display text-[24px] font-bold lg:text-[32px]">
              Cómo le fue a {nombre}
            </h2>
            <ChipDeEstado chip={chip} />
          </div>
          {items.length > 0 && (
            <Constelacion
              estados={estrellas}
              forma={formaDe(p.id)}
              ancho={160}
              r={8}
              guias={!completa}
              lineas={completa}
              className="w-[110px] lg:w-[160px]"
            />
          )}
        </div>
        {cuerpo && <p className="m-0 text-[14px] leading-[1.5] text-ink-2">{cuerpo}</p>}
        {empezada && (
          <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
            <Contador n={p.firstTry} etiqueta="al primer intento" fondo="#DEF3E7" tinta="#1F5238" I={Check} />
            <Contador n={p.secondTry} etiqueta="al segundo" fondo="#FFEBC7" tinta="#6B440A" I={RotateCcw} />
            <Contador n={p.shown} etiqueta="se le mostró" fondo="#F3EEFB" tinta="#4A3A75" I={Eye} />
            <Contador n={p.skipped} etiqueta="saltados" fondo="#F6EFE8" tinta="#33203B" I={SkipForward} />
          </div>
        )}
        {leCosto.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <strong className="text-[14px]">Le costó:</strong>
            {leCosto.map((t) => (
              <span key={t} className="inline-flex min-h-8 items-center rounded-pill bg-durazno-soft px-3 py-1 text-[14px] font-bold text-[#6B3E1A]">
                {t}
              </span>
            ))}
          </div>
        )}
      </div>

      {items.length > 0 && (
        <div className="grid items-start gap-3.5 lg:grid-cols-2">
          {items.map((e) => (
            <ResultadoEjercicio key={e.index} ejercicio={e} />
          ))}
        </div>
      )}
    </section>
  );
}

/** El encabezado según el momento del set (§10.11): preparándose, lista, en curso, completada o vencida. */
function encabezado(p: PracticaDelActa, nombre: string, resultados: ResultadoDelEjercicio[]): { chip: Chip; cuerpo: string | null } {
  const cuenta = (r: ResultadoDelEjercicio) => resultados.filter((x) => x === r).length;
  switch (p.status) {
    case "PENDING":
      return {
        chip: { texto: "Preparándose", fondo: "#F4EAE0", tinta: "#5E4E6B", I: Loader },
        cuerpo: "La práctica se está armando desde tu acta. Tarda cerca de un minuto.",
      };
    case "READY":
      return {
        chip: { texto: `Lista · ${nombre} aún no empieza`, fondo: "#EFE9F9", tinta: "#4A3A75", I: PlayCircle },
        cuerpo: `Ya la tiene en su perfil. Vence el ${diaCorto(p.expiresAt)}.`,
      };
    case "IN_PROGRESS": {
      const hechos = resultados.filter((r) => r !== "sinHacer").length;
      const partes = [
        cuenta("primero") ? `${numeroEnPalabras(cuenta("primero"))} al primer intento` : null,
        cuenta("segundo") ? `${numeroEnPalabras(cuenta("segundo"))} al segundo` : null,
        cuenta("mostrada") ? `${numeroEnPalabras(cuenta("mostrada"))} se le mostró` : null,
        cuenta("saltado") ? `${numeroEnPalabras(cuenta("saltado"))} saltada` : null,
      ].filter(Boolean);
      return {
        chip: { texto: `En curso · va en la ${hechos + 1}`, fondo: "#FFE9D6", tinta: "#6B3E1A", I: PlayCircle },
        cuerpo: partes.length ? `Lleva ${partes.join(" y ")}. Lo demás aparece cuando lo haga.` : "Ya empezó. Lo demás aparece cuando lo haga.",
      };
    }
    case "COMPLETED": {
      const partes = [
        `${cuenta("primero")} al primer intento`,
        cuenta("segundo") ? `${cuenta("segundo")} al segundo` : null,
        cuenta("mostrada") ? `${cuenta("mostrada")} se le mostró` : null,
        cuenta("saltado") ? `${cuenta("saltado")} saltado${cuenta("saltado") === 1 ? "" : "s"}` : null,
      ].filter(Boolean);
      return {
        chip: { texto: `Completada${p.completedAt ? ` · ${diaCorto(p.completedAt)}` : ""}`, fondo: "#DEF3E7", tinta: "#1F5238", I: Check },
        cuerpo: `${partes.join(", ")}.`,
      };
    }
    case "EXPIRED":
      return {
        chip: { texto: "Vencida sin hacer", fondo: "#FFFFFF", tinta: "#5E4E6B", borde: "1.5px dashed #C9B8A8", I: Clock },
        cuerpo: `No la hizo antes del ${diaCorto(p.expiresAt)}. Puede ser buen tema para abrir la clase.`,
      };
    default:
      return {
        chip: { texto: "Sin práctica esta vez", fondo: "#F4EAE0", tinta: "#5E4E6B", I: Minus },
        cuerpo:
          "Esta vez no salieron ejercicios: el acta no tenía palabras ni frases concretas en que anclarlos. " +
          "La próxima, anota el vocabulario y los errores de la clase.",
      };
  }
}

function ChipDeEstado({ chip }: { chip: Chip }) {
  return (
    <span
      className="inline-flex h-[30px] items-center gap-1.5 self-start rounded-pill px-3 text-[13px] font-bold"
      style={{ background: chip.fondo, color: chip.tinta, border: chip.borde ?? "none" }}
    >
      <chip.I size={14} strokeWidth={2} aria-hidden />
      {chip.texto}
    </span>
  );
}

function Contador({ n, etiqueta, fondo, tinta, I }: { n: number; etiqueta: string; fondo: string; tinta: string; I: LucideIcon }) {
  return (
    <div className="flex items-center gap-2.5 rounded-pareja px-3 py-2.5" style={{ background: fondo, color: tinta }}>
      <I size={18} strokeWidth={2} className="shrink-0" aria-hidden />
      <span className="flex flex-col">
        <strong className="font-display text-[22px] leading-none">{n}</strong>
        <span className="text-[12px] font-bold">{etiqueta}</span>
      </span>
    </div>
  );
}

/** Una tarjeta por ejercicio (§9.10): qué vio, qué respondió en cada intento y qué se esperaba. */
function ResultadoEjercicio({ ejercicio: e }: { ejercicio: EjercicioDelActa }) {
  const r = resultadoDe(e);
  const c = RESULTADO[r];
  const cat = CATEGORIA[e.category];
  const intentos: { texto: string; bien: boolean }[] = [];
  if (e.firstAnswer) intentos.push({ texto: mostrarRespuesta(e.type, e.payload, e.firstAnswer), bien: e.attempts === 1 && e.correct === true });
  if (e.secondAnswer) intentos.push({ texto: mostrarRespuesta(e.type, e.payload, e.secondAnswer), bien: e.correct === true });
  // Parejas se une par por par: a la primera no queda ningún par fallado que mostrar, solo el acierto.
  if (e.type === "MATCH_MEANING" && e.correct === true && intentos.length === 0) {
    const n = (leerPayload<{ terms?: string[] }>(e).terms ?? []).length;
    intentos.push({ texto: n === 1 ? "La pareja unida" : `Las ${numeroEnPalabras(n)} parejas unidas`, bien: true });
  }
  const termino = leerPayload<{ term?: string }>(e).term ?? e.sourceTerm;

  return (
    <article className="flex flex-col overflow-hidden rounded-tarjeta bg-white">
      <div className="flex flex-wrap items-center justify-between gap-2.5 px-[18px] py-3" style={{ background: c.cabecera }}>
        <span className="flex items-center gap-2 text-[13px] font-extrabold">
          <span className="tracking-[.06em] uppercase" style={{ color: cat.tinta }}>
            {NOMBRE_DE_CATEGORIA[e.category]}
          </span>
          <span className="font-bold text-ink-2">{NOMBRE_DEL_TIPO[e.type]}</span>
        </span>
        <span
          className="inline-flex h-7 items-center gap-[5px] rounded-pill px-2.5 text-[12px] font-extrabold"
          style={{ background: c.fondo, color: c.tinta, border: c.borde ?? "none" }}
        >
          <c.I size={13} strokeWidth={2.2} aria-hidden />
          {c.texto}
        </span>
      </div>
      <div className="flex flex-col gap-2.5 px-[18px] pt-3.5 pb-4">
        <div className="flex flex-col gap-0.5">
          <span className="text-[12px] font-bold text-ink-3">Lo que vio</span>
          <span className="text-[15px] leading-[1.45] font-semibold">{loQueVio(e)}</span>
        </div>
        <div className="flex flex-col gap-1.5">
          <span className="text-[12px] font-bold text-ink-3">Lo que respondió</span>
          {intentos.length === 0 && (
            <span className="text-[14px] text-ink-2">
              {r === "saltado" ? "Lo saltó: su dispositivo no tenía voz en inglés." : "Todavía nada."}
            </span>
          )}
          {intentos.map((i, k) => (
            <div key={k} className="flex items-start gap-2 text-[14px] leading-[1.45]">
              <span className="min-w-[74px] shrink-0 font-bold text-ink-2">Intento {k + 1}</span>
              <span lang="en" className="flex-1">
                {i.texto}
              </span>
              <span className="flex shrink-0 items-center gap-1 text-[12px] font-extrabold" style={{ color: i.bien ? "#2E6B4A" : "#8A5A12" }}>
                {i.bien ? <Check size={13} strokeWidth={2.2} aria-hidden /> : <RotateCcw size={13} strokeWidth={2.2} aria-hidden />}
                {i.bien ? "Bien" : "Casi"}
              </span>
            </div>
          ))}
        </div>
        <div className="flex flex-col gap-1 border-t border-line-soft pt-2.5 text-[14px] leading-[1.5]">
          <span>
            <span className="font-bold text-ink-3">Esperada · </span>
            <strong lang="en">
              {e.type === "WRITE_SENTENCE"
                ? `Cualquier frase de verdad que use ${termino ?? "la palabra"}`
                : e.expected
                  ? mostrarEsperada(e.type, e.expected)
                  : "—"}
            </strong>
          </span>
          {e.explanation && <span className="text-ink-2">{e.explanation}</span>}
        </div>
      </div>
    </article>
  );
}

/** Lo que el estudiante tuvo delante, en una línea. */
function loQueVio(e: EjercicioDelActa): string {
  switch (e.type) {
    case "FILL_BLANK": {
      const p = leerPayload<{ sentence?: string; options?: string[] }>(e);
      return `«${p.sentence}» — elegir la que va en el hueco${p.options?.length ? `: ${p.options.join(", ")}` : ""}`;
    }
    case "FIX_SENTENCE":
      return `«${leerPayload<{ sentence?: string }>(e).sentence}» — escribirla bien`;
    case "MATCH_MEANING": {
      const p = leerPayload<{ terms?: string[] }>(e);
      return `${(p.terms ?? []).join(" · ")}, con sus significados en español`;
    }
    case "ORDER_DIALOGUE":
      return `${(leerPayload<{ lines?: string[] }>(e).lines ?? []).length} líneas de una conversación, desordenadas`;
    case "WRITE_SENTENCE":
      return `Escribir una frase suya con «${leerPayload<{ term?: string }>(e).term}»`;
    case "SPOT_ERROR":
      return `«${unirFichas(leerPayload<{ tokens?: string[] }>(e).tokens ?? [])}» — tocar la palabra que está mal`;
    case "BUILD_SENTENCE": {
      const p = leerPayload<{ guide?: string }>(e);
      return p.guide ? `Armar en inglés «${p.guide}»` : "Armar una frase con fichas";
    }
    case "CHOOSE_REPLY": {
      const p = leerPayload<{ from?: string; message?: string }>(e);
      return `${p.from ? `${p.from}: ` : ""}«${p.message}»`;
    }
    case "LISTEN_CHOOSE": {
      const p = leerPayload<{ say?: string; options?: string[] }>(e);
      return `Meissa dice «${p.say}». Opciones: ${(p.options ?? []).join(", ")}`;
    }
    case "DICTATION":
      return `Meissa dice «${leerPayload<{ say?: string }>(e).say}» y lo escribe`;
  }
}
