"use client";

import { ArrowDown } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Meissa } from "@/components/Meissa";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import type { PagedProfessors } from "@/lib/api/types";
import { useMediaQuery } from "@/lib/useMediaQuery";
import {
  type Caja,
  guardarPaso,
  type PasoDelRecorrido,
  type Recorrido as Definicion,
  ubicarTarjeta,
  type Ubicacion,
} from "@/lib/recorrido";

/**
 * El recorrido guiado, idéntico al handoff (§7): velo noche al 74 %, el elemento recortado con un
 * margen de su fondo y un anillo durazno de 3 px, y al lado la tarjeta del paso con Rigel —o
 * Meissa en el de la práctica—, su contador, su progreso y «Saltar · Atrás · Siguiente».
 *
 * <p>Cada paso <strong>lleva a su pantalla</strong> y espera a que aparezca lo que explica; si el
 * elemento está fuera de la vista, primero sale la píldora «Te llevo hasta allá», se desliza hasta
 * dejarlo a un tercio de la altura y solo entonces aparece la tarjeta. Con movimiento reducido,
 * salto directo y sin píldora.
 *
 * <p>Mientras está abierto, lo de atrás es `inert`: un clic en el velo no hace nada y el elemento
 * iluminado no recibe clics. Esc es «Saltar» y devuelve el foco a donde estaba; ← → navegan; el
 * foco arranca en «Siguiente» y no sale de la tarjeta. El paso se guarda: si se cierra la pestaña,
 * al volver Rigel pregunta «¿Seguimos donde íbamos?».
 */

export type Arranque = "inicio" | "paso1" | { reanudar: number };

type Fase = { tipo: "inicio" } | { tipo: "reanudar"; paso: number } | { tipo: "paso"; i: number } | { tipo: "cierre" };

const VELO = "rgba(46,30,78,.74)";
const ESPERA_ANCLA_MS = 3000;
const ESPERA_ANCLA_PREFERIDA_MS = 1800;

export function Recorrido({
  definicion,
  nombre,
  arranque,
  onTerminar,
}: {
  definicion: Definicion;
  nombre: string;
  arranque: Arranque;
  /** Al saltarlo, al decir «Ahora no» o al cerrarlo desde el final: todos lo dan por visto. */
  onTerminar: () => void;
}) {
  const router = useRouter();
  const total = definicion.pasos.length;
  const [fase, setFase] = useState<Fase>(() =>
    arranque === "inicio"
      ? { tipo: "inicio" }
      : arranque === "paso1"
        ? { tipo: "paso", i: 0 }
        : { tipo: "reanudar", paso: arranque.reanudar },
  );
  const raiz = useRef<HTMLDivElement | null>(null);
  const antes = useRef<Element | null>(null);

  // Lo de atrás queda inerte, y el foco vuelve adonde estaba al salir.
  useEffect(() => {
    antes.current = document.activeElement;
    const hermanos = Array.from(document.body.children).filter((el) => el !== raiz.current) as HTMLElement[];
    const previos = hermanos.map((el) => el.inert);
    hermanos.forEach((el) => (el.inert = true));
    const desborde = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      hermanos.forEach((el, i) => (el.inert = previos[i]));
      document.body.style.overflow = desborde;
      if (antes.current instanceof HTMLElement) antes.current.focus();
    };
  }, []);

  // El paso en curso se guarda para poder retomarlo; al terminar o saltar se olvida.
  useEffect(() => {
    if (fase.tipo === "paso") guardarPaso(definicion.id, fase.i + 1);
  }, [fase, definicion.id]);

  const terminar = useCallback(() => {
    guardarPaso(definicion.id, null);
    onTerminar();
  }, [definicion.id, onTerminar]);

  const irA = useCallback(
    (i: number) => setFase(i < 0 ? { tipo: "inicio" } : i >= total ? { tipo: "cierre" } : { tipo: "paso", i }),
    [total],
  );

  if (typeof document === "undefined") return null;

  return createPortal(
    <div
      ref={(el) => {
        raiz.current = el;
      }}
      className="fixed inset-0 z-[80]"
    >
      {fase.tipo === "inicio" && (
        <ModalDelRecorrido
          pose={definicion.inicio.pose}
          titulo={definicion.inicio.titulo}
          texto={definicion.inicio.texto}
          principal={{ etiqueta: "Empezar", accion: () => irA(0) }}
          secundaria={{ etiqueta: "Ahora no", accion: terminar }}
          onEscape={terminar}
        />
      )}
      {fase.tipo === "reanudar" && (
        <ModalDelRecorrido
          pose="saludo"
          titulo="¿Seguimos donde íbamos?"
          texto={`Te quedaste en «${definicion.pasos[fase.paso - 1]?.titulo ?? ""}», el paso ${fase.paso} de ${total}.`}
          principal={{ etiqueta: "Seguir", accion: () => irA(fase.paso - 1) }}
          secundaria={{ etiqueta: "Ahora no", accion: terminar }}
          onEscape={terminar}
        />
      )}
      {fase.tipo === "cierre" && (
        <ModalDelRecorrido
          pose="celebracion"
          titulo={definicion.cierre.titulo(nombre)}
          texto={definicion.cierre.texto}
          principal={{
            etiqueta: definicion.cierre.principal.etiqueta,
            accion: () => {
              terminar();
              router.push(definicion.cierre.principal.href);
            },
          }}
          secundaria={{
            etiqueta: definicion.cierre.secundaria.etiqueta,
            accion: () => {
              terminar();
              router.push(definicion.cierre.secundaria.href);
            },
          }}
          onEscape={terminar}
        />
      )}
      {fase.tipo === "paso" && (
        <PasoConFoco
          key={fase.i}
          paso={definicion.pasos[fase.i]}
          indice={fase.i}
          total={total}
          onSiguiente={() => irA(fase.i + 1)}
          onAtras={() => irA(fase.i - 1)}
          onSaltar={terminar}
        />
      )}
    </div>,
    document.body,
  );
}

/* ------------------------------------------------------------------ un paso */

type Foco = { caja: Caja; radio: string; fondo: string };

function PasoConFoco({
  paso,
  indice,
  total,
  onSiguiente,
  onAtras,
  onSaltar,
}: {
  paso: PasoDelRecorrido;
  indice: number;
  total: number;
  onSiguiente: () => void;
  onAtras: () => void;
  onSaltar: () => void;
}) {
  const router = useRouter();
  const movil = !useMediaQuery("(min-width: 640px)");
  const menosMovimiento = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [elemento, setElemento] = useState<HTMLElement | null>(null);
  const [listo, setListo] = useState(false);
  const [pildora, setPildora] = useState(false);
  const [foco, setFoco] = useState<Foco | null>(null);
  const [ubicacion, setUbicacion] = useState<Ubicacion | null>(null);
  const tarjeta = useRef<HTMLDivElement>(null);
  const siguiente = useRef<HTMLButtonElement>(null);

  // 1 · Llevar a la pantalla del paso y esperar a que aparezca lo que se explica. Cada paso monta
  // su propio componente (key = índice), así que el estado arranca limpio sin reiniciarlo aquí.
  useEffect(() => {
    let vivo = true;
    (async () => {
      const ruta = paso.ruta === "profesor" ? await rutaDelPrimerProfesor() : paso.ruta;
      if (!vivo) return;
      if (ruta && window.location.pathname + window.location.search !== ruta) router.push(ruta);
      const el = await esperarAncla(paso.anclas, () => vivo);
      if (!vivo) return;
      setElemento(el);
      if (el && fueraDeVista(el)) {
        // Fuera de pantalla: la píldora, el desliz hasta un tercio de la altura y luego la tarjeta.
        if (!menosMovimiento) {
          setPildora(true);
          await pausa(450);
        }
        const r = el.getBoundingClientRect();
        window.scrollTo({ top: window.scrollY + r.top - window.innerHeight / 3, behavior: menosMovimiento ? "auto" : "smooth" });
        if (!menosMovimiento) await pausa(520);
        if (!vivo) return;
        setPildora(false);
      }
      setListo(true);
    })();
    return () => {
      vivo = false;
    };
  }, [paso, router, menosMovimiento]);

  // 2 · Medir el foco y ubicar la tarjeta; se vuelve a medir al redimensionar o desplazar.
  const medir = useCallback(() => {
    if (!listo) return;
    const f = elemento && document.contains(elemento) ? focoDe(elemento) : null;
    setFoco(f);
    const alto = tarjeta.current?.offsetHeight ?? 240;
    setUbicacion(ubicarTarjeta(f?.caja ?? null, alto, { width: window.innerWidth, height: window.innerHeight }, movil));
  }, [elemento, listo, movil]);

  useLayoutEffect(() => {
    const cuadro = requestAnimationFrame(medir);
    return () => cancelAnimationFrame(cuadro);
  }, [medir]);

  useEffect(() => {
    window.addEventListener("resize", medir);
    window.addEventListener("scroll", medir, true);
    return () => {
      window.removeEventListener("resize", medir);
      window.removeEventListener("scroll", medir, true);
    };
  }, [medir]);

  // 3 · El foco del teclado arranca en «Siguiente» y no sale de la tarjeta.
  useEffect(() => {
    if (listo && ubicacion) siguiente.current?.focus({ preventScroll: true });
  }, [listo, ubicacion]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onSaltar();
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        onSiguiente();
      } else if (e.key === "ArrowLeft" && indice > 0) {
        e.preventDefault();
        onAtras();
      } else if (e.key === "Tab") {
        atraparFoco(e, tarjeta.current);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [indice, onAtras, onSaltar, onSiguiente]);

  const tituloId = useId();
  const textoId = useId();
  const ultimo = indice === total - 1;
  const conMeissa = paso.guia === "meissa";

  return (
    <>
      {/* El velo: atrapa los clics y no hace nada con ellos. Con foco, el oscurecido lo pone la sombra del recorte. */}
      <div className="absolute inset-0" style={foco ? undefined : { background: VELO }} aria-hidden="true" />
      {foco && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute transition-all duration-[260ms] ease-[cubic-bezier(.2,0,0,1)] motion-reduce:transition-none"
          style={{
            top: foco.caja.top + 9,
            left: foco.caja.left + 9,
            width: foco.caja.width - 18,
            height: foco.caja.height - 18,
            borderRadius: foco.radio,
            boxShadow: `0 0 0 6px ${foco.fondo}, 0 0 0 9px #FFC189, 0 0 0 9999px ${VELO}`,
          }}
        />
      )}

      {pildora && (
        <div
          role="status"
          className="anim-rise absolute bottom-[108px] left-1/2 flex h-12 -translate-x-1/2 items-center gap-2.5 whitespace-nowrap rounded-pill bg-white py-0 pl-2 pr-5 shadow-[0_18px_44px_rgba(46,30,78,.35)]"
        >
          <span className="grid h-9 w-9 place-items-center rounded-full bg-[#EFE9F9]">
            {conMeissa ? <Meissa decorativo recorte="22 14 156 156" className="h-7 w-7" /> : <RigelMini className="h-7 w-7" />}
          </span>
          <span className="text-[14px] font-bold text-[#33203B]">Te llevo hasta allá</span>
          <ArrowDown size={18} strokeWidth={1.75} className="text-[#33203B]" aria-hidden />
        </div>
      )}

      <p className="sr-only" aria-live="polite">
        {listo ? `Paso ${indice + 1} de ${total}: ${paso.titulo}` : ""}
      </p>

      {listo && (
        <div
          ref={tarjeta}
          role="dialog"
          aria-modal="true"
          aria-labelledby={tituloId}
          aria-describedby={textoId}
          className="anim-rise absolute z-10 flex flex-col gap-3.5 rounded-[22px] bg-white p-5 shadow-[0_18px_44px_rgba(46,30,78,.35)]"
          style={
            ubicacion
              ? { top: ubicacion.top, left: ubicacion.left, width: ubicacion.width }
              : { top: -9999, left: 0, width: movil ? "calc(100vw - 40px)" : 360 }
          }
        >
          {ubicacion?.flecha != null && <Flecha lugar={ubicacion.lugar} en={ubicacion.flecha} />}

          <div className="flex items-center gap-3">
            <span
              className={`grid h-[52px] w-[52px] shrink-0 place-items-center rounded-full ${conMeissa ? "bg-[#EFE9F9]" : "bg-[#FFF1C9]"}`}
              aria-hidden="true"
            >
              {conMeissa ? (
                <Meissa decorativo recorte="22 14 156 156" className="h-10 w-10" />
              ) : (
                <RigelMini />
              )}
            </span>
            <div className="flex flex-1 flex-col gap-[7px]">
              <span className="text-[12px] font-bold text-[#5E4E6B]">
                {indice + 1} de {total}
                {conMeissa && " · Meissa"}
              </span>
              <Progreso actual={indice} total={total} />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <h2 id={tituloId} className="font-display text-[19px] font-bold leading-[1.2] text-[#33203B]">
              {paso.titulo}
            </h2>
            <p id={textoId} className="m-0 text-[15px] leading-[1.5] text-[#5E4E6B] [text-wrap:pretty]">
              {paso.texto}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onSaltar}
              className="h-11 rounded-pill px-2.5 text-[14px] font-semibold text-[#5E4E6B] transition-colors hover:bg-[#F4EAE0] hover:text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none"
            >
              Saltar
            </button>
            <span className="flex-1" />
            <button
              type="button"
              onClick={onAtras}
              disabled={indice === 0}
              className="h-11 rounded-pill border-[1.5px] border-[#EADFD4] px-[18px] text-[14px] font-semibold text-[#33203B] transition-colors hover:border-[#D8C7B8] hover:bg-[#FFF6EE] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none disabled:pointer-events-none disabled:opacity-45"
            >
              Atrás
            </button>
            <button
              ref={siguiente}
              type="button"
              onClick={onSiguiente}
              className="h-11 rounded-pill bg-[#E8503A] px-5 text-[14px] font-bold text-[#FFF6EE] transition-colors hover:bg-[#C0341F] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none"
            >
              {ultimo ? "Terminar" : "Siguiente"}
            </button>
          </div>

          <span className="hidden text-[12px] text-[#7A6B85] lg:block">Esc para salir · ← → para moverte</span>
        </div>
      )}
    </>
  );
}

/** El Rigel de la tarjeta: solo la estrella y su cara, recortado para 40 px (handoff, línea 285). */
function RigelMini({ className = "h-10 w-10" }: { className?: string }) {
  return (
    <svg viewBox="22 14 156 156" aria-hidden="true" className={className}>
      <polygon
        points="100,36 115.3,75 157.1,77.5 124.7,104 135.3,144.5 100,122 64.7,144.5 75.3,104 42.9,77.5 84.7,75"
        fill="#FFCE3A"
        stroke="#FFCE3A"
        strokeWidth="26"
        strokeLinejoin="round"
        paintOrder="stroke fill"
      />
      <ellipse cx="70" cy="106" rx="10" ry="6.5" fill="#FF8E76" opacity=".8" />
      <ellipse cx="130" cy="106" rx="10" ry="6.5" fill="#FF8E76" opacity=".8" />
      <circle cx="85" cy="91" r="10" fill="#2B2430" />
      <circle cx="115" cy="91" r="10" fill="#2B2430" />
      <circle cx="81.5" cy="87" r="3.4" fill="#FFF6EE" />
      <circle cx="111.5" cy="87" r="3.4" fill="#FFF6EE" />
      <path d="M87,103 Q100,116 113,103" stroke="#5A2436" strokeWidth="5" strokeLinecap="round" fill="none" />
    </svg>
  );
}

/** `<TourProgress>`: hechos 8 lavanda, el actual 20 tinta, los que faltan 8 `#EADFD4`. Decorativo. */
function Progreso({ actual, total }: { actual: number; total: number }) {
  return (
    <div aria-hidden="true" className="flex items-center gap-1">
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className={`h-1 rounded-pill ${i === actual ? "w-5 bg-[#33203B]" : i < actual ? "w-2 bg-[#B9A7E6]" : "w-2 bg-[#EADFD4]"}`}
        />
      ))}
    </div>
  );
}

/** El cuadrado blanco de 16 a 45° que asoma 7 px hacia el foco. */
function Flecha({ lugar, en }: { lugar: Ubicacion["lugar"]; en: number }) {
  const base = "absolute h-4 w-4 rotate-45 rounded-[3px] bg-white";
  if (lugar === "abajo") return <span aria-hidden="true" className={base} style={{ top: -7, left: en }} />;
  if (lugar === "arriba") return <span aria-hidden="true" className={base} style={{ bottom: -7, left: en }} />;
  if (lugar === "derecha") return <span aria-hidden="true" className={base} style={{ left: -7, top: en }} />;
  if (lugar === "izquierda") return <span aria-hidden="true" className={base} style={{ right: -7, top: en }} />;
  return null;
}

/* ------------------------------------------------------------------ inicio, reanudar y cierre */

function ModalDelRecorrido({
  pose,
  titulo,
  texto,
  principal,
  secundaria,
  onEscape,
}: {
  pose: "profe" | "saludo" | "celebracion";
  titulo: string;
  texto: ReactNode;
  principal: { etiqueta: string; accion: () => void };
  secundaria: { etiqueta: string; accion: () => void };
  onEscape: () => void;
}) {
  const tituloId = useId();
  const textoId = useId();
  const caja = useRef<HTMLDivElement>(null);
  const primero = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    primero.current?.focus({ preventScroll: true });
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onEscape();
      } else if (e.key === "Tab") {
        atraparFoco(e, caja.current);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onEscape]);

  return (
    <div className="absolute inset-0 flex items-center justify-center px-5" style={{ background: VELO }}>
      <div
        ref={caja}
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        aria-describedby={textoId}
        className="anim-modal flex w-full flex-col items-center gap-3.5 rounded-[22px] bg-white px-[22px] pb-5 pt-6 text-center shadow-[0_18px_44px_rgba(46,30,78,.35)] sm:w-[440px] sm:px-8 sm:pb-6 sm:pt-8"
      >
        <Rigel pose={pose} decorativo className="h-[156px] w-[148px] sm:h-[178px] sm:w-[170px]" />
        <h2 id={tituloId} className="font-display text-[24px] font-bold leading-[1.15] text-[#33203B] sm:text-[26px]">
          {titulo}
        </h2>
        <p id={textoId} className="m-0 text-[15px] leading-[1.55] text-[#5E4E6B] [text-wrap:pretty]">
          {texto}
        </p>
        <div className="flex w-full flex-col gap-1.5 pt-1 sm:w-auto sm:flex-row-reverse sm:justify-center sm:gap-2.5 sm:pt-1.5">
          <button
            ref={primero}
            type="button"
            onClick={principal.accion}
            className="h-[52px] rounded-pill bg-[#E8503A] px-6 text-[15px] font-bold text-[#FFF6EE] shadow-[0_10px_24px_rgba(232,80,58,.35)] transition-colors hover:bg-[#C0341F] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none"
          >
            {principal.etiqueta}
          </button>
          <button
            type="button"
            onClick={secundaria.accion}
            className="h-12 rounded-pill px-5 text-[15px] font-semibold text-[#5E4E6B] transition-colors hover:bg-[#F4EAE0] hover:text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none sm:h-[52px]"
          >
            {secundaria.etiqueta}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ utilidades */

function atraparFoco(e: KeyboardEvent, caja: HTMLElement | null) {
  if (!caja) return;
  const enfocables = Array.from(caja.querySelectorAll<HTMLElement>("button:not([disabled]), a[href]"));
  if (enfocables.length === 0) return;
  const primero = enfocables[0];
  const ultimo = enfocables[enfocables.length - 1];
  if (e.shiftKey && document.activeElement === primero) {
    e.preventDefault();
    ultimo.focus();
  } else if (!e.shiftKey && document.activeElement === ultimo) {
    e.preventDefault();
    primero.focus();
  } else if (!caja.contains(document.activeElement)) {
    e.preventDefault();
    primero.focus();
  }
}

function pausa(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

/** El primer elemento visible con ese `data-tour`: la barra lateral o la inferior, el que se vea. */
function visible(ancla: string): HTMLElement | null {
  const candidatos = Array.from(document.querySelectorAll<HTMLElement>(`[data-tour="${ancla}"]`));
  return (
    candidatos.find((el) => {
      const r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== "hidden";
    }) ?? null
  );
}

/**
 * Espera a que la pantalla pinte lo que el paso explica. El ancla preferida tiene un rato para
 * aparecer —la página acaba de cambiar y está cargando sus datos—; pasado ese rato vale la
 * siguiente de la lista, que al final es siempre la navegación.
 */
async function esperarAncla(anclas: string[], sigue: () => boolean): Promise<HTMLElement | null> {
  const inicio = Date.now();
  while (sigue() && Date.now() - inicio < ESPERA_ANCLA_MS) {
    const preferida = visible(anclas[0]);
    if (preferida) return preferida;
    if (Date.now() - inicio > ESPERA_ANCLA_PREFERIDA_MS) {
      for (const a of anclas.slice(1)) {
        const el = visible(a);
        if (el) return el;
      }
    }
    await pausa(100);
  }
  for (const a of anclas) {
    const el = visible(a);
    if (el) return el;
  }
  return null;
}

function fueraDeVista(el: HTMLElement): boolean {
  const r = el.getBoundingClientRect();
  return r.top < 0 || r.bottom > window.innerHeight - 16;
}

/** La caja del anillo por fuera (elemento + 6 de margen + 3 de anillo), su radio y su fondo local. */
function focoDe(el: HTMLElement): Foco {
  const r = el.getBoundingClientRect();
  const estilo = getComputedStyle(el);
  const radio = estilo.borderTopLeftRadius && estilo.borderTopLeftRadius !== "0px" ? estilo.borderTopLeftRadius : "14px";
  return {
    caja: { top: r.top - 9, left: r.left - 9, width: r.width + 18, height: r.height + 18 },
    radio,
    fondo: fondoDe(el.parentElement),
  };
}

/** El color del primer ancestro con fondo propio: crema en la página, blanco en una tarjeta o la barra. */
function fondoDe(el: HTMLElement | null): string {
  for (let n = el; n; n = n.parentElement) {
    const c = getComputedStyle(n).backgroundColor;
    if (c && c !== "transparent" && !/rgba\(.*,\s*0\)$/.test(c)) return c;
  }
  return "#FFF6EE";
}

async function rutaDelPrimerProfesor(): Promise<string> {
  try {
    const pagina = await apiFetch<PagedProfessors>("/api/v1/professors?page=0&size=1");
    const id = pagina.content?.[0]?.id;
    return id ? `/profesores/${id}` : "/profesores";
  } catch {
    return "/profesores";
  }
}
