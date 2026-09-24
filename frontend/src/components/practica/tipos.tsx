"use client";

import { ChevronDown, ChevronUp, GripVertical } from "lucide-react";
import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from "react";
import {
  leerPayload,
  palabrasNuevasDe,
  partirLinea,
  unirFichas,
  type Ejercicio,
} from "@/lib/practica";
import type { useVozEnIngles } from "@/lib/voz";
import {
  BurbujaEllos,
  BurbujaTu,
  Campo,
  claseDe,
  Escribiendo,
  etiquetaDe,
  Ficha,
  IconoDeVariante,
  Reproducir,
  SinVoz,
  type EstadoCampo,
  type Variante,
} from "./piezas";

/**
 * Los diez tipos de ejercicio (handoff `design_handoff_orion_practica`, §8 y copy-y-estados). Cada
 * uno pinta su bloque según la fase que le pasa la pantalla y le devuelve la respuesta cuando está
 * lista para comprobar. La pantalla decide la fase; el tipo solo sabe dibujarla.
 *
 * <p>Se montan de nuevo en cada intento (la pantalla les cambia la `key`): el estado inicial de un
 * segundo intento sale del ejercicio —lo que respondió la primera vez—, no de lo que quedó en
 * pantalla. Y no se puede comprobar hasta cambiar algo: lo que respondió la primera vez ya se sabe
 * que no era, y mandarlo otra vez solo gastaría el último intento.
 */

export type Fase = "inicial" | "interactuando" | "enviando" | "correcto" | "casi" | "mostrada" | "sinvoz" | "saltado";

export type Voz = ReturnType<typeof useVozEnIngles>;

export type PropsTipo = {
  item: Ejercicio;
  fase: Fase;
  /** La respuesta, cuando está lista para comprobar; `tocado` aunque todavía no lo esté. */
  onRespuesta: (respuesta: string | null, tocado: boolean) => void;
};

/** Cerrado o comprobándose: ya no se toca. */
function quieto(fase: Fase): boolean {
  return fase === "enviando" || fase === "correcto" || fase === "mostrada" || fase === "saltado" || fase === "sinvoz";
}

function esperadaDe<T>(item: Ejercicio): T | null {
  if (!item.expected) return null;
  try {
    return JSON.parse(item.expected) as T;
  } catch {
    return null;
  }
}

/** Texto suelto de la frase: una palabra por pieza, para que el renglón se parta donde debe. */
function Palabras({ texto }: { texto: string }) {
  return (
    <>
      {texto
        .split(/\s+/)
        .filter(Boolean)
        .map((w, i) => (
          <span key={i}>{w}</span>
        ))}
    </>
  );
}

const SUB = "bg-white lg:bg-crema";
const ROTULO = "text-[12px] font-bold tracking-[.08em] text-ink-3 uppercase";

/* ------------------------------------------------------------------------------------------------
 * 1 · Parejas
 * ---------------------------------------------------------------------------------------------- */

export function Parejas({
  item,
  fase,
  pendiente,
  fallo,
  onUnir,
  onTocar,
}: {
  item: Ejercicio;
  fase: Fase;
  /** El par que está viajando al servidor. */
  pendiente: [string, string] | null;
  /** El último par que no fue: ámbar y una sacudida. */
  fallo: [string, string] | null;
  onUnir: (termino: string, significado: string) => void;
  /** Cualquier toque saca de «casi». */
  onTocar: () => void;
}) {
  const p = leerPayload<{ terms?: string[]; meanings?: string[] }>(item);
  const terminos = p.terms ?? [];
  const significados = p.meanings ?? [];
  const unidos = leerUnidos(item.answer);
  const numero = new Map<string, number>();
  Object.entries(unidos).forEach(([t, m], i) => {
    numero.set(`L:${t}`, i + 1);
    numero.set(`R:${m}`, i + 1);
  });
  const esperados = esperadaDe<Record<string, string>>(item) ?? {};
  const [elegida, setElegida] = useState<{ lado: "L" | "R"; texto: string } | null>(null);
  const botones = useRef<Map<string, HTMLButtonElement>>(new Map());

  const bloqueado = quieto(fase) || pendiente !== null;
  const unidoL = (t: string) => numero.has(`L:${t}`);
  const unidoR = (m: string) => numero.has(`R:${m}`);

  const variante = (lado: "L" | "R", texto: string): Variante => {
    const unido = lado === "L" ? unidoL(texto) : unidoR(texto);
    if (fase === "correcto") return "bien";
    if (unido) return "unida";
    if (fase === "mostrada") return "mostrada";
    if (pendiente && (lado === "L" ? pendiente[0] === texto : pendiente[1] === texto)) return "elegida";
    if (fase === "casi" && fallo && (lado === "L" ? fallo[0] === texto : fallo[1] === texto)) return "casi";
    if (elegida && elegida.lado === lado && elegida.texto === texto) return "elegida";
    return "normal";
  };

  const tocar = (lado: "L" | "R", texto: string) => {
    if (bloqueado) return;
    if (lado === "L" ? unidoL(texto) : unidoR(texto)) return;
    onTocar();
    if (!elegida || elegida.lado === lado) {
      setElegida(elegida && elegida.texto === texto && elegida.lado === lado ? null : { lado, texto });
      return;
    }
    const [t, m] = lado === "L" ? [texto, elegida.texto] : [elegida.texto, texto];
    setElegida(null);
    onUnir(t, m);
  };

  // Teclado (§9.3): las flechas se mueven entre tarjetas; Enter elige o intenta unir.
  const mover = (e: ReactKeyboardEvent, lado: "L" | "R", i: number) => {
    const lista = lado === "L" ? terminos : significados;
    let destino: string | null = null;
    if (e.key === "ArrowDown") destino = `${lado}:${lista[Math.min(i + 1, lista.length - 1)]}`;
    if (e.key === "ArrowUp") destino = `${lado}:${lista[Math.max(i - 1, 0)]}`;
    if (e.key === "ArrowRight" && lado === "L") destino = `R:${significados[Math.min(i, significados.length - 1)]}`;
    if (e.key === "ArrowLeft" && lado === "R") destino = `L:${terminos[Math.min(i, terminos.length - 1)]}`;
    if (destino) {
      e.preventDefault();
      botones.current.get(destino)?.focus();
    }
  };

  const tarjeta = (lado: "L" | "R", texto: string, i: number) => {
    const v = variante(lado, texto);
    const n = numero.get(`${lado}:${texto}`);
    const sacude = fase === "casi" && v === "casi";
    // En «mostrada», el par que faltaba: su compañero va en el texto para lectores de pantalla.
    const pareja =
      fase === "mostrada" && v === "mostrada"
        ? lado === "L"
          ? esperados[texto]
          : Object.keys(esperados).find((k) => esperados[k] === texto)
        : null;
    return (
      <button
        key={`${lado}${texto}`}
        ref={(b) => {
          if (b) botones.current.set(`${lado}:${texto}`, b);
          else botones.current.delete(`${lado}:${texto}`);
        }}
        type="button"
        lang={lado === "L" ? "en" : "es"}
        onClick={() => tocar(lado, texto)}
        onKeyDown={(e) => mover(e, lado, i)}
        aria-disabled={bloqueado || v === "unida" || undefined}
        aria-label={`${etiquetaDe(texto, v)}${n ? `, pareja ${n}` : ""}${pareja ? `, va con ${pareja}` : ""}`}
        className={`flex min-h-14 items-center gap-2 rounded-pareja px-3.5 text-left text-[16px] leading-[1.2] transition-[transform,box-shadow] duration-180 ease-out-soft lg:text-[17px] ${
          lado === "L" ? "font-bold" : "font-semibold"
        } ${claseDe(v, !bloqueado)} ${sacude ? "pr-sacudida" : ""} ${v === "unida" ? "pr-pop" : ""}`}
      >
        {n !== undefined && v === "unida" && (
          <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full bg-lavanda-ink text-[11px] font-extrabold text-crema">
            {n}
          </span>
        )}
        <span className="flex-1">{texto}</span>
        <IconoDeVariante v={v} />
      </button>
    );
  };

  return (
    <div className="grid grid-cols-2 gap-2.5">
      <div role="group" aria-label="Palabras en inglés" className="flex flex-col gap-2.5">
        {terminos.map((t, i) => tarjeta("L", t, i))}
      </div>
      <div role="group" aria-label="Significados en español" className="flex flex-col gap-2.5">
        {significados.map((m, i) => tarjeta("R", m, i))}
      </div>
    </div>
  );
}

export function leerUnidos(answer: string | null): Record<string, string> {
  if (!answer || !answer.startsWith("{")) return {};
  try {
    return JSON.parse(answer) as Record<string, string>;
  } catch {
    return {};
  }
}

/* ------------------------------------------------------------------------------------------------
 * 2 · Completa
 * ---------------------------------------------------------------------------------------------- */

export function Completa({ item, fase, onRespuesta }: PropsTipo) {
  const p = leerPayload<{ sentence?: string; options?: string[] }>(item);
  const [antes, despues] = partirHueco(p.sentence ?? "");
  const [puesta, setPuesta] = useState<string | null>(null);
  const bloqueado = quieto(fase);

  const poner = (o: string | null) => {
    setPuesta(o);
    onRespuesta(o, true);
  };

  // En el hueco: lo que puso, o —mostrada— la que iba.
  const enHueco = fase === "mostrada" ? item.expected : fase === "correcto" ? (item.answer ?? puesta) : puesta;
  const vHueco: Variante =
    fase === "casi" ? "casi" : fase === "correcto" ? "bien" : fase === "mostrada" ? "mostrada" : "puesta";
  const terminado = fase === "correcto" || fase === "mostrada" || fase === "enviando";

  return (
    <>
      <div
        lang="en"
        className="flex flex-wrap items-center gap-x-1.5 gap-y-2 py-2 font-display text-[22px] leading-[1.5] font-semibold lg:text-[26px]"
      >
        <Palabras texto={antes} />
        {enHueco ? (
          <Ficha
            key={enHueco}
            texto={enHueco}
            v={vHueco}
            onToque={bloqueado ? undefined : () => poner(null)}
            className="pr-pop min-h-[46px] px-3.5 font-sans"
            grande
          />
        ) : (
          <span
            aria-label="Hueco vacío"
            role="img"
            className="inline-block h-[46px] w-[132px] rounded-ficha border-2 border-dashed border-hueco-line bg-crema"
          />
        )}
        <Palabras texto={despues} />
      </div>
      <div role="group" aria-label="Fichas" lang="en" className="flex flex-wrap gap-2.5">
        {(p.options ?? []).map((o) => (
          <Ficha
            key={o}
            texto={o}
            v={o === enHueco ? "usada" : terminado ? "apagada" : "normal"}
            onToque={bloqueado ? undefined : () => poner(o)}
            className="lg:text-[17px]"
          />
        ))}
      </div>
    </>
  );
}

function partirHueco(frase: string): [string, string] {
  const partes = frase.split(/_{2,}/);
  if (partes.length < 2) return [frase, ""];
  return [partes[0], partes.slice(1).join(" ")];
}

/* ------------------------------------------------------------------------------------------------
 * Campos de texto: Corrige, Escucha y escribe, Tu frase
 * ---------------------------------------------------------------------------------------------- */

function estadoDelCampo(fase: Fase): EstadoCampo {
  switch (fase) {
    case "casi":
      return "casi";
    case "correcto":
      return "bien";
    case "mostrada":
      return "mostrada";
    case "enviando":
      return "enviando";
    case "sinvoz":
    case "saltado":
      return "deshabilitado";
    default:
      return "vacio";
  }
}

function useTexto(item: Ejercicio, fase: Fase, onRespuesta: PropsTipo["onRespuesta"]) {
  // En el segundo intento vuelve lo que escribió la primera vez: se corrige, no se reescribe.
  const [valor, setValor] = useState(() => (item.attempts > 0 && !item.closed ? (item.answer ?? "") : ""));
  const ref = useRef<HTMLTextAreaElement>(null);
  const cambiar = (v: string) => {
    setValor(v);
    onRespuesta(v.trim() ? v : null, v.length > 0);
  };
  // «Casi…»: el campo recupera el foco (§11). Y al volver para el segundo intento, también.
  useEffect(() => {
    if (fase === "casi" || (item.attempts > 0 && !item.closed && fase === "inicial")) ref.current?.focus();
  }, [fase, item.attempts, item.closed]);
  const mostrado = fase === "correcto" || fase === "mostrada" ? (item.answer ?? valor) : valor;
  return { valor: mostrado, cambiar, ref };
}

export function Corrige({ item, fase, onRespuesta }: PropsTipo) {
  const p = leerPayload<{ sentence?: string; accepted?: string[] }>(item);
  const t = useTexto(item, fase, onRespuesta);
  return (
    <>
      <div className={`flex flex-col gap-1.5 rounded-sub px-[18px] py-4 ${SUB}`}>
        <span className={ROTULO}>La frase de la clase</span>
        <span lang="en" className="font-display text-[22px] leading-[1.3] font-semibold lg:text-[26px]">
          {p.sentence}
        </span>
      </div>
      <Campo
        campoRef={t.ref}
        etiqueta="Tu versión"
        valor={t.valor}
        onCambio={t.cambiar}
        placeholder="Escríbela bien aquí"
        alto={96}
        estado={estadoDelCampo(fase)}
        ayuda={fase === "casi" ? "Cambia lo que haga falta y vuelve a comprobar." : null}
        soloLectura={quieto(fase)}
      />
    </>
  );
}

export function TuFrase({ item, fase, onRespuesta }: PropsTipo) {
  const termino = leerPayload<{ term?: string }>(item).term ?? "";
  const t = useTexto(item, fase, onRespuesta);
  return (
    <Campo
      campoRef={t.ref}
      etiqueta="Tu frase"
      valor={t.valor}
      onCambio={t.cambiar}
      placeholder="Por ejemplo, sobre algo que te pasó hoy…"
      alto={112}
      estado={estadoDelCampo(fase)}
      ayuda={fase === "inicial" && termino ? `Cualquier frase de verdad que use ${termino} está bien.` : null}
      soloLectura={quieto(fase)}
    />
  );
}

/* ------------------------------------------------------------------------------------------------
 * 4 · Caza el error
 * ---------------------------------------------------------------------------------------------- */

export function CazaElError({ item, fase, onRespuesta }: PropsTipo) {
  const fichas = leerPayload<{ tokens?: string[] }>(item).tokens ?? [];
  const [elegida, setElegida] = useState<number | null>(null);
  const esperada = esperadaDe<{ index?: number; correction?: string }>(item);
  const bloqueado = quieto(fase);
  const respondida = item.answer !== null ? Number.parseInt(item.answer, 10) : null;

  const variante = (i: number): Variante => {
    if (fase === "correcto") return i === respondida ? "bien" : "normal";
    if (fase === "mostrada") return i === esperada?.index ? "mostrada" : "normal";
    if (fase === "casi") return i === elegida ? "casi" : "normal";
    return i === elegida ? "elegida" : "normal";
  };

  const correccion = esperada?.correction;
  const nuevas = correccion ? palabrasNuevasDe(fichas, correccion) : new Set<number>();

  return (
    <>
      <div role="group" aria-label="Palabras de la frase" lang="en" className="flex flex-wrap gap-2.5">
        {fichas.map((f, i) => (
          <Ficha
            key={i}
            texto={f}
            v={variante(i)}
            onToque={
              bloqueado
                ? undefined
                : () => {
                    const nueva = elegida === i ? null : i;
                    setElegida(nueva);
                    onRespuesta(nueva === null ? null : String(nueva), true);
                  }
            }
            className="lg:text-[17px]"
          />
        ))}
      </div>
      {(fase === "correcto" || fase === "mostrada") && correccion && (
        <div className={`flex flex-col gap-1 rounded-sub px-[18px] py-3.5 ${SUB}`}>
          <span className={ROTULO}>Bien dicha</span>
          <span lang="en" className="font-display text-[22px] leading-[1.35] font-semibold lg:text-[26px]">
            {correccion.split(/\s+/).map((w, i) => (
              <span key={i}>
                {i > 0 && " "}
                {nuevas.has(i) ? <mark className="rounded-[6px] bg-rigel-soft px-1 text-ink">{w}</mark> : w}
              </span>
            ))}
          </span>
        </div>
      )}
    </>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 5 · Arma la frase
 * ---------------------------------------------------------------------------------------------- */

export function ArmaLaFrase({ item, fase, onRespuesta }: PropsTipo) {
  const p = leerPayload<{ tiles?: string[]; guide?: string }>(item);
  const fichas = p.tiles ?? [];
  // En el segundo intento, las fichas vuelven a donde las puso: se corrige el orden, no se arma de cero.
  const [puestas, setPuestas] = useState<number[]>(() => (item.attempts > 0 && !item.closed ? indicesDe(fichas, item.answer) : []));
  const bloqueado = quieto(fase);

  const cambiar = (nuevas: number[]) => {
    setPuestas(nuevas);
    onRespuesta(nuevas.length === fichas.length ? JSON.stringify(nuevas.map((i) => fichas[i])) : null, true);
  };

  const vLinea: Variante = fase === "casi" ? "casi" : fase === "correcto" ? "bien" : fase === "mostrada" ? "mostrada" : "puesta";
  const enLinea: { texto: string; i: number | null }[] =
    fase === "mostrada"
      ? (esperadaDe<string[]>(item) ?? []).map((texto) => ({ texto, i: null }))
      : puestas.map((i) => ({ texto: fichas[i], i }));

  return (
    <>
      {p.guide && (
        <div className="flex flex-col gap-1">
          <span className={ROTULO}>En español</span>
          <span className="font-display text-[22px] leading-[1.25] font-bold lg:text-[26px]">{p.guide}</span>
        </div>
      )}
      <div
        aria-label={`Tu frase: ${unirFichas(enLinea.map((f) => f.texto))}`}
        role="group"
        lang="en"
        className="flex min-h-16 flex-wrap items-end gap-2 border-b-2 border-dashed border-hueco-line px-1 pt-2 pb-2.5"
      >
        {enLinea.map((f, k) => (
          <Ficha
            key={`${f.i ?? "e"}-${k}`}
            texto={f.texto}
            v={vLinea}
            onToque={bloqueado || f.i === null ? undefined : () => cambiar(puestas.filter((x) => x !== f.i))}
            className="pr-pop lg:text-[17px]"
          />
        ))}
        {enLinea.length === 0 && <span className="pb-3 text-[15px] text-ink-3">Toca las fichas en orden</span>}
      </div>
      <div role="group" aria-label="Fichas" lang="en" className="flex flex-wrap justify-center gap-2.5">
        {fichas.map((f, i) => (
          <Ficha
            key={i}
            texto={f}
            v={puestas.includes(i) || fase === "mostrada" ? "usada" : "normal"}
            onToque={bloqueado ? undefined : () => cambiar([...puestas, i])}
            className="lg:text-[17px]"
          />
        ))}
      </div>
    </>
  );
}

/** Las fichas de una respuesta anterior, de vuelta a sus posiciones en el banco. */
function indicesDe(fichas: string[], respuesta: string | null): number[] {
  if (!respuesta) return [];
  try {
    const palabras = JSON.parse(respuesta) as string[];
    const usados = new Set<number>();
    const out: number[] = [];
    for (const w of palabras) {
      const i = fichas.findIndex((f, k) => f === w && !usados.has(k));
      if (i < 0) return [];
      usados.add(i);
      out.push(i);
    }
    return out;
  } catch {
    return [];
  }
}

/* ------------------------------------------------------------------------------------------------
 * 6 · Ordena la conversación
 * ---------------------------------------------------------------------------------------------- */

const TINTA_DE_QUIEN = ["#5E4A8A", "#A8321F"];

export function OrdenaLaConversacion({ item, fase, onRespuesta }: PropsTipo) {
  const inicial = leerPayload<{ lines?: string[] }>(item).lines ?? [];
  const [lineas, setLineas] = useState<string[]>(() => {
    if (item.attempts > 0 && !item.closed && item.answer) {
      try {
        return JSON.parse(item.answer) as string[];
      } catch {
        return inicial;
      }
    }
    return inicial;
  });
  const [arrastrando, setArrastrando] = useState<number | null>(null);
  const filas = useRef<(HTMLLIElement | null)[]>([]);
  const bloqueado = quieto(fase);

  const quienes = Array.from(new Set(inicial.map((l) => partirLinea(l).quien).filter(Boolean))) as string[];
  const tintaDe = (quien: string | null) => TINTA_DE_QUIEN[Math.max(0, quienes.indexOf(quien ?? "")) % 2];

  const poner = (nuevas: string[]) => {
    setLineas(nuevas);
    onRespuesta(JSON.stringify(nuevas), true);
  };
  const mover = (i: number, hacia: number) => {
    const j = i + hacia;
    if (bloqueado || j < 0 || j >= lineas.length) return;
    const nuevas = [...lineas];
    [nuevas[i], nuevas[j]] = [nuevas[j], nuevas[i]];
    poner(nuevas);
  };

  // Arrastrar con el dedo o el ratón: la línea sigue al puntero y cambia de sitio al pasar la mitad
  // de la vecina. Las flechas hacen lo mismo sin arrastrar (§14).
  const empezar = (e: ReactPointerEvent, i: number) => {
    if (bloqueado) return;
    e.preventDefault();
    (e.target as Element).setPointerCapture?.(e.pointerId);
    setArrastrando(i);
  };
  const seguir = (e: ReactPointerEvent) => {
    if (arrastrando === null) return;
    const y = e.clientY;
    const actual = arrastrando;
    const arriba = filas.current[actual - 1]?.getBoundingClientRect();
    const abajo = filas.current[actual + 1]?.getBoundingClientRect();
    if (arriba && y < arriba.top + arriba.height / 2) {
      const nuevas = [...lineas];
      [nuevas[actual - 1], nuevas[actual]] = [nuevas[actual], nuevas[actual - 1]];
      poner(nuevas);
      setArrastrando(actual - 1);
    } else if (abajo && y > abajo.top + abajo.height / 2) {
      const nuevas = [...lineas];
      [nuevas[actual + 1], nuevas[actual]] = [nuevas[actual], nuevas[actual + 1]];
      poner(nuevas);
      setArrastrando(actual + 1);
    }
  };
  const soltar = () => setArrastrando(null);

  const mostradas = fase === "mostrada" ? (esperadaDe<string[]>(item) ?? lineas) : lineas;
  const variante = (i: number): Variante =>
    fase === "casi" ? "casi" : fase === "correcto" ? "bien" : fase === "mostrada" ? "mostrada" : arrastrando === i ? "elegida" : "normal";

  return (
    <ol aria-label="Conversación para ordenar" className="m-0 flex list-none flex-col gap-2 p-0" onPointerMove={seguir} onPointerUp={soltar} onPointerCancel={soltar}>
      {mostradas.map((l, i) => {
        const { quien, texto } = partirLinea(l);
        const v = variante(i);
        return (
          <li
            key={l}
            ref={(el) => {
              filas.current[i] = el;
            }}
            onKeyDown={(e) => {
              // Alt + ↑/↓ mueve la línea que tiene el foco.
              if (!e.altKey) return;
              if (e.key === "ArrowUp") {
                e.preventDefault();
                mover(i, -1);
              }
              if (e.key === "ArrowDown") {
                e.preventDefault();
                mover(i, 1);
              }
            }}
            className={`flex items-center gap-1 rounded-sub py-1.5 pr-1 pl-1.5 transition-[transform,box-shadow] duration-160 ${claseDe(v, false)} ${
              v === "elegida" ? "rotate-[-1.2deg]" : ""
            }`}
          >
            <span
              aria-hidden
              onPointerDown={(e) => empezar(e, i)}
              className={`flex h-11 w-7 shrink-0 touch-none items-center justify-center text-ink-3 ${bloqueado ? "" : "cursor-grab active:cursor-grabbing"}`}
            >
              <GripVertical size={16} strokeWidth={2.5} />
            </span>
            <span className="flex min-w-0 flex-1 flex-col gap-0.5 py-1">
              {quien && (
                <span className="text-[11px] font-extrabold tracking-[.08em] uppercase" style={{ color: tintaDe(quien) }}>
                  {quien}
                </span>
              )}
              <span lang="en" className="text-[15px] leading-[1.35] font-semibold">
                {texto}
              </span>
            </span>
            <IconoDeVariante v={v} size={18} />
            {!bloqueado && (
              <span className="flex shrink-0 flex-col">
                <button
                  type="button"
                  aria-label={`Subir: ${texto}`}
                  disabled={i === 0}
                  onClick={() => mover(i, -1)}
                  className="flex h-7 w-11 cursor-pointer items-center justify-center text-ink-2 disabled:opacity-30"
                >
                  <ChevronUp size={18} strokeWidth={2} />
                </button>
                <button
                  type="button"
                  aria-label={`Bajar: ${texto}`}
                  disabled={i === mostradas.length - 1}
                  onClick={() => mover(i, 1)}
                  className="flex h-7 w-11 cursor-pointer items-center justify-center text-ink-2 disabled:opacity-30"
                >
                  <ChevronDown size={18} strokeWidth={2} />
                </button>
              </span>
            )}
          </li>
        );
      })}
    </ol>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 7 · Responde en el chat
 * ---------------------------------------------------------------------------------------------- */

export function RespondeEnElChat({ item, fase, onRespuesta }: PropsTipo) {
  const p = leerPayload<{ from?: string; message?: string; options?: string[] }>(item);
  const quien = p.from?.trim() || "Mensaje";
  // El mensaje llega con «escribiendo…» (900 ms) la primera vez; en el segundo intento ya está ahí.
  const [llego, setLlego] = useState(item.attempts > 0 || item.closed);
  const [elegida, setElegida] = useState<string | null>(null);
  const usadas = item.attempts > 0 && item.answer ? [item.answer] : [];
  const bloqueado = quieto(fase) || !llego;

  useEffect(() => {
    if (llego) return;
    const t = window.setTimeout(() => setLlego(true), 900);
    return () => window.clearTimeout(t);
  }, [llego]);

  let tuya: { texto: string; estado: "enviando" | "bien" | "casi" | "mostrada" } | null = null;
  if (fase === "enviando" && elegida) tuya = { texto: elegida, estado: "enviando" };
  if (fase === "casi" && item.answer) tuya = { texto: item.answer, estado: "casi" };
  if (fase === "correcto" && item.answer) tuya = { texto: item.answer, estado: "bien" };
  if (fase === "mostrada" && item.expected) tuya = { texto: item.expected, estado: "mostrada" };
  const conSugeridas = fase !== "enviando" && fase !== "correcto" && fase !== "mostrada";

  return (
    <>
      <div role="log" aria-label={`Chat con ${quien}`} className={`flex flex-col gap-2.5 rounded-tarjeta p-3.5 ${SUB}`}>
        <div className="flex items-center gap-2 pb-1">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-coral-soft text-[12px] font-extrabold text-[#A8321F]" aria-hidden>
            {quien.charAt(0).toUpperCase()}
          </span>
          <span className="text-[13px] font-bold text-ink-2">{quien}</span>
        </div>
        {llego ? <BurbujaEllos texto={p.message ?? ""} /> : <Escribiendo quien={quien} />}
        {tuya && <BurbujaTu texto={tuya.texto} estado={tuya.estado} />}
        {fase === "correcto" && <Escribiendo quien={quien} />}
      </div>
      {conSugeridas && (
        <div role="group" aria-label="Respuestas sugeridas" lang="en" className="flex flex-col items-end gap-2">
          {(p.options ?? []).map((o) => {
            const v: Variante = !llego ? "apagada" : usadas.includes(o) ? "usada" : elegida === o && fase !== "casi" ? "elegida" : "normal";
            const inerte = bloqueado || v === "usada" || fase === "casi";
            return (
              <button
                key={o}
                type="button"
                aria-disabled={inerte || undefined}
                aria-hidden={v === "usada" || undefined}
                tabIndex={v === "usada" ? -1 : undefined}
                onClick={() => {
                  if (bloqueado || v === "usada") return;
                  setElegida(o);
                  onRespuesta(o, true);
                }}
                className={`flex min-h-11 max-w-[88%] items-center gap-1.5 rounded-[18px_18px_6px_18px] px-4 py-2.5 text-left text-[15px] leading-[1.35] font-semibold transition-[transform,box-shadow] duration-180 ease-out-soft ${claseDe(v, !inerte)}`}
              >
                {o}
              </button>
            );
          })}
        </div>
      )}
    </>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 8 y 9 · Escucha (con Meissa)
 * ---------------------------------------------------------------------------------------------- */

function useReproductor(item: Ejercicio, fase: Fase, voz: Voz) {
  const dice = leerPayload<{ say?: string }>(item).say ?? "";
  // En «casi» se activa «Más despacio» (§9.5); en el segundo intento, también.
  const [despacio, setDespacio] = useState(item.attempts > 0 && !item.closed);
  const [antes, setAntes] = useState(fase);
  if (fase !== antes) {
    setAntes(fase);
    if (fase === "casi") setDespacio(true);
  }
  return {
    reproducir: (
      <Reproducir
        sonando={voz.hablando}
        despacio={despacio}
        enCasi={fase === "casi"}
        deshabilitado={!voz.disponible}
        sub={SUB}
        onTocar={() => (voz.hablando ? voz.callar() : voz.hablar(dice, despacio))}
        onRepetir={() => voz.hablar(dice, despacio)}
        onDespacio={() => {
          const nuevo = !despacio;
          setDespacio(nuevo);
          voz.hablar(dice, nuevo);
        }}
      />
    ),
  };
}

export function EscuchaYElige({ item, fase, onRespuesta, voz }: PropsTipo & { voz: Voz }) {
  const opciones = leerPayload<{ options?: string[] }>(item).options ?? [];
  const [elegida, setElegida] = useState<string | null>(null);
  const r = useReproductor(item, fase, voz);
  const sinVoz = fase === "sinvoz" || fase === "saltado";
  const bloqueado = quieto(fase);

  const variante = (o: string): Variante => {
    if (sinVoz) return "apagada";
    if (fase === "correcto") return o === item.answer ? "bien" : "normal";
    if (fase === "mostrada") return o === item.expected ? "mostrada" : "normal";
    if (fase === "casi") return o === elegida ? "casi" : "normal";
    return o === elegida ? "elegida" : "normal";
  };
  const radio: Partial<Record<Variante, string>> = {
    elegida: "6px solid #2E1E4E",
    bien: "6px solid #2E6B4A",
    casi: "6px solid #B8741A",
    mostrada: "6px solid #7A64C0",
  };

  return (
    <>
      {sinVoz ? <SinVoz /> : r.reproducir}
      <div role="radiogroup" aria-label="Opciones" className="flex flex-col gap-2.5">
        {opciones.map((o) => {
          const v = variante(o);
          return (
            <button
              key={o}
              type="button"
              role="radio"
              aria-checked={v === "elegida" || v === "bien" || v === "casi"}
              aria-disabled={bloqueado || undefined}
              aria-label={etiquetaDe(o, v)}
              onClick={() => {
                if (bloqueado) return;
                setElegida(o);
                onRespuesta(o, true);
              }}
              className={`flex min-h-14 items-center gap-3 rounded-pareja px-4 text-left text-[16px] font-bold transition-[transform,box-shadow] duration-180 ease-out-soft lg:text-[17px] ${claseDe(v, !bloqueado)}`}
            >
              <span className="box-border h-5 w-5 shrink-0 rounded-full" style={{ border: radio[v] ?? "2px solid #B8A99B" }} aria-hidden />
              <span className="flex-1">{o}</span>
              <IconoDeVariante v={v} size={18} />
            </button>
          );
        })}
      </div>
    </>
  );
}

export function EscuchaYEscribe({ item, fase, onRespuesta, voz }: PropsTipo & { voz: Voz }) {
  const r = useReproductor(item, fase, voz);
  const t = useTexto(item, fase, onRespuesta);
  const sinVoz = fase === "sinvoz" || fase === "saltado";
  return (
    <>
      {sinVoz ? <SinVoz /> : r.reproducir}
      <Campo
        campoRef={t.ref}
        etiqueta="Lo que oíste"
        valor={t.valor}
        onCambio={t.cambiar}
        placeholder="Escribe lo que dice Meissa"
        alto={64}
        estado={estadoDelCampo(fase)}
        soloLectura={quieto(fase)}
      />
    </>
  );
}
