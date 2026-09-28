"use client";

import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { ArrowDown, ArrowUp } from "lucide-react";
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
  deslizHastaSuSitio,
  guardarPaso,
  type PasoDelRecorrido,
  type Recorrido as Definicion,
  ubicarTarjeta,
  type Ubicacion,
  type Vista,
} from "@/lib/recorrido";

/**
 * El recorrido guiado, idéntico al handoff (§7): velo noche al 74 %, el elemento recortado con un
 * margen de su fondo y un anillo durazno de 3 px, y al lado la tarjeta del paso con Rigel —o
 * Meissa en el de la práctica—, su contador, su progreso y «Saltar · Atrás · Siguiente».
 *
 * <p>Cada paso <strong>lleva a su pantalla</strong> y espera a que aparezca lo que explica, sin
 * plazos fijos: mira el DOM y los datos de la pantalla, y en cuanto la pantalla terminó de cargar
 * sin pintar lo preferido, ilumina la navegación. Si lo que explica está en la página, primero sale
 * la píldora «Te llevo hasta allá», la página se desliza —siempre, así sea un poco— hasta dejarlo a
 * un tercio de la altura y, al llegar, aparece la tarjeta. Con movimiento reducido, salto directo y
 * sin píldora. La pantalla del paso siguiente se precarga mientras se lee el actual.
 *
 * <p>Mientras está abierto, lo de atrás es `inert`: un clic en el velo no hace nada y el elemento
 * iluminado no recibe clics. Esc es «Saltar» —salvo en el obligatorio, que no se salta— y devuelve el
 * foco a donde estaba; ← → navegan; el foco arranca en «Siguiente» y no sale de la tarjeta. El paso
 * se guarda: si se cierra la pestaña, al volver Rigel pregunta «¿Seguimos donde íbamos?».
 */

export type Arranque = "inicio" | "paso1" | { reanudar: number };

type Fase = { tipo: "inicio" } | { tipo: "reanudar"; paso: number } | { tipo: "paso"; i: number } | { tipo: "cierre" };

const VELO = "rgba(46,30,78,.74)";
/** El tope por si la pantalla nunca llega (una redirección, la red caída): vale lo que haya. */
const ESPERA_MAXIMA_MS = 2500;
/** Cuánto tiene que estar quieta la pantalla —sin datos por llegar— para dar por hecho que ya pintó. */
const PANTALLA_QUIETA_MS = 120;
/** La píldora asoma antes de que la página se mueva (su animación de entrada dura 220). */
const PILDORA_ANTES_MS = 150;
/** El desliz suave lo cronometra el navegador; esto es solo por si nunca avisa que terminó. */
const DESLIZ_MAXIMO_MS = 1000;

export function Recorrido({
  definicion,
  nombre,
  arranque,
  obligatorio = false,
  onTerminar,
}: {
  definicion: Definicion;
  nombre: string;
  arranque: Arranque;
  /**
   * Sin «Ahora no», sin «Saltar» y sin Esc: solo se sale terminándolo. El del profe recién aprobado
   * (Pardo, 28/09/2026: «el video de Sofía es opcional, el recorrido NO»).
   */
  obligatorio?: boolean;
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

  // El perfil del primer profe se averigua una vez por recorrido, no cada vez que se pasa por ahí.
  const primerProfesor = useRef<Promise<string> | null>(null);
  const rutaDe = useCallback((paso: PasoDelRecorrido): Promise<string | null> => {
    if (paso.ruta !== "profesor") return Promise.resolve(paso.ruta);
    primerProfesor.current ??= rutaDelPrimerProfesor();
    return primerProfesor.current;
  }, []);

  // Mientras se lee un paso, la pantalla del siguiente (y la del anterior) ya se está cargando: al
  // pulsar «Siguiente» la navegación no espera a la red. `next dev` no precarga; producción sí.
  useEffect(() => {
    const vecinos =
      fase.tipo === "inicio"
        ? [0]
        : fase.tipo === "reanudar"
          ? [fase.paso - 1]
          : fase.tipo === "paso"
            ? [fase.i + 1, fase.i - 1]
            : [];
    for (const i of vecinos) {
      const paso = definicion.pasos[i];
      if (paso) void rutaDe(paso).then((ruta) => ruta && router.prefetch(ruta));
    }
  }, [fase, definicion.pasos, rutaDe, router]);

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
          secundaria={obligatorio ? undefined : { etiqueta: "Ahora no", accion: terminar }}
          onEscape={obligatorio ? undefined : terminar}
        />
      )}
      {fase.tipo === "reanudar" && (
        <ModalDelRecorrido
          pose="saludo"
          titulo="¿Seguimos donde íbamos?"
          texto={`Te quedaste en «${definicion.pasos[fase.paso - 1]?.titulo ?? ""}», el paso ${fase.paso} de ${total}.`}
          principal={{ etiqueta: "Seguir", accion: () => irA(fase.paso - 1) }}
          secundaria={obligatorio ? undefined : { etiqueta: "Ahora no", accion: terminar }}
          onEscape={obligatorio ? undefined : terminar}
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
          rutaDe={rutaDe}
          indice={fase.i}
          total={total}
          onSiguiente={() => irA(fase.i + 1)}
          onAtras={() => irA(fase.i - 1)}
          onSaltar={obligatorio ? undefined : terminar}
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
  rutaDe,
  indice,
  total,
  onSiguiente,
  onAtras,
  onSaltar,
}: {
  paso: PasoDelRecorrido;
  rutaDe: (paso: PasoDelRecorrido) => Promise<string | null>;
  indice: number;
  total: number;
  onSiguiente: () => void;
  onAtras: () => void;
  /** Sin él, el recorrido es obligatorio: no hay «Saltar» y Esc no hace nada. */
  onSaltar?: () => void;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const movil = !useMediaQuery("(min-width: 640px)");
  const menosMovimiento = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [elemento, setElemento] = useState<HTMLElement | null>(null);
  const [listo, setListo] = useState(false);
  const [pildora, setPildora] = useState<"abajo" | "arriba" | null>(null);
  const [foco, setFoco] = useState<Foco | null>(null);
  const [ubicacion, setUbicacion] = useState<Ubicacion | null>(null);
  const tarjeta = useRef<HTMLDivElement>(null);
  const siguiente = useRef<HTMLButtonElement>(null);

  // 1 · Llevar a la pantalla del paso y esperar a que aparezca lo que se explica. Cada paso monta
  // su propio componente (key = índice), así que el estado arranca limpio sin reiniciarlo aquí.
  useEffect(() => {
    const cancelado = new AbortController();
    const vivo = () => !cancelado.signal.aborted;
    (async () => {
      const ruta = await rutaDe(paso);
      if (!vivo()) return;
      if (ruta && dondeEstoy() !== ruta) router.push(ruta);
      const el = await esperarAncla(
        paso.anclas,
        { enRuta: () => !ruta || dondeEstoy() === ruta, datos: queryClient },
        cancelado.signal,
      );
      if (!vivo()) return;
      setElemento(el);
      // La navegación es fija y no se desplaza; lo que está en la página, sí.
      if (el && !el.dataset.tour?.startsWith("nav:")) {
        const d = deslizHastaSuSitio(el.getBoundingClientRect(), vistaActual());
        if (menosMovimiento) {
          // Sin animaciones: salto directo, y solo si hace falta para verlo.
          if (d !== null && fueraDeVista(el)) window.scrollTo({ top: window.scrollY + d, behavior: "auto" });
        } else if (d !== null) {
          // La píldora, el desliz hasta su sitio y, al llegar, la tarjeta.
          setPildora(d > 0 ? "abajo" : "arriba");
          await pausa(PILDORA_ANTES_MS);
          if (!vivo()) return;
          const destino = window.scrollY + d;
          window.scrollTo({ top: destino, behavior: "smooth" });
          await finDelDesliz(destino, vivo);
          if (!vivo()) return;
          setPildora(null);
        }
      }
      setListo(true);
    })();
    return () => cancelado.abort();
  }, [paso, rutaDe, router, queryClient, menosMovimiento]);

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
    // Si la página se reacomoda debajo (llegan más datos arriba del elemento), el foco lo sigue.
    const cambios = new ResizeObserver(medir);
    cambios.observe(document.body);
    if (elemento) cambios.observe(elemento);
    return () => {
      window.removeEventListener("resize", medir);
      window.removeEventListener("scroll", medir, true);
      cambios.disconnect();
    };
  }, [medir, elemento]);

  // 3 · El foco del teclado arranca en «Siguiente» y no sale de la tarjeta.
  useEffect(() => {
    if (listo && ubicacion) siguiente.current?.focus({ preventScroll: true });
  }, [listo, ubicacion]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onSaltar?.();
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
          {pildora === "arriba" ? (
            <ArrowUp size={18} strokeWidth={1.75} className="text-[#33203B]" aria-hidden />
          ) : (
            <ArrowDown size={18} strokeWidth={1.75} className="text-[#33203B]" aria-hidden />
          )}
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
            {onSaltar && (
              <button
                type="button"
                onClick={onSaltar}
                className="h-11 rounded-pill px-2.5 text-[14px] font-semibold text-[#5E4E6B] transition-colors hover:bg-[#F4EAE0] hover:text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none"
              >
                Saltar
              </button>
            )}
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

          <span className="hidden text-[12px] text-[#7A6B85] lg:block">
            {onSaltar ? "Esc para salir · ← → para moverte" : "← → para moverte"}
          </span>
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
  /** Sin ella, solo queda el botón principal (el recorrido obligatorio). */
  secundaria?: { etiqueta: string; accion: () => void };
  /** Sin él, Esc no cierra. */
  onEscape?: () => void;
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
        onEscape?.();
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
          {secundaria && (
            <button
              type="button"
              onClick={secundaria.accion}
              className="h-12 rounded-pill px-5 text-[15px] font-semibold text-[#5E4E6B] transition-colors hover:bg-[#F4EAE0] hover:text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none sm:h-[52px]"
            >
              {secundaria.etiqueta}
            </button>
          )}
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

function primeraVisible(anclas: string[]): HTMLElement | null {
  for (const a of anclas) {
    const el = visible(a);
    if (el) return el;
  }
  return null;
}

function dondeEstoy(): string {
  return window.location.pathname + window.location.search;
}

/**
 * Espera, sin sondear, a que la pantalla pinte lo que el paso explica. Mira dos cosas: el DOM (cada
 * cambio se revisa una vez por cuadro) y los datos de la pantalla (TanStack Query).
 *
 * <p>El ancla preferida gana en cuanto aparece. Si la pantalla ya llegó, no le queda nada por cargar
 * —cuenta lo que carga por primera vez, no el refresco periódico de los mensajes— y aun así no la
 * pintó, ya no la va a pintar (el primer día no hay clase que unirse): vale la siguiente de la lista,
 * que al final es siempre la navegación. Antes esa espera era un plazo fijo de 1,8 s, y era lo que
 * se sentía como «un par de segundos» en los pasos de las clases.
 */
function esperarAncla(
  anclas: string[],
  { enRuta, datos }: { enRuta: () => boolean; datos: QueryClient },
  cancelado: AbortSignal,
): Promise<HTMLElement | null> {
  return new Promise((resolver) => {
    if (cancelado.aborted) return resolver(null);
    let cuadro = 0;
    let quietaDesde: number | null = null;
    let reloj: ReturnType<typeof setTimeout> | undefined;
    const cargando = () => datos.isFetching({ predicate: (q) => q.state.status === "pending" }) > 0;

    const programar = () => {
      if (!cuadro) cuadro = requestAnimationFrame(revisar);
    };
    const observador = new MutationObserver(programar);
    const desuscribir = datos.getQueryCache().subscribe(programar);
    const tope = setTimeout(() => terminar(primeraVisible(anclas)), ESPERA_MAXIMA_MS);
    const alCancelar = () => terminar(null);

    function terminar(el: HTMLElement | null) {
      observador.disconnect();
      desuscribir();
      cancelAnimationFrame(cuadro);
      clearTimeout(reloj);
      clearTimeout(tope);
      cancelado.removeEventListener("abort", alCancelar);
      resolver(el);
    }

    function revisar() {
      cuadro = 0;
      if (!enRuta()) return;
      const preferida = visible(anclas[0]);
      if (preferida) return terminar(preferida);
      if (cargando()) {
        quietaDesde = null;
        return;
      }
      const ahora = performance.now();
      quietaDesde ??= ahora;
      const falta = PANTALLA_QUIETA_MS - (ahora - quietaDesde);
      if (falta <= 0) return terminar(primeraVisible(anclas.slice(1)));
      clearTimeout(reloj);
      reloj = setTimeout(programar, falta);
    }

    cancelado.addEventListener("abort", alCancelar);
    observador.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["class", "style", "hidden"],
    });
    programar();
  });
}

/** Termina cuando el desliz suave llegó o se detuvo: lo mira cuadro a cuadro, sin plazo fijo. */
function finDelDesliz(destino: number, sigue: () => boolean): Promise<void> {
  return new Promise((resolver) => {
    const inicio = performance.now();
    let antes = window.scrollY;
    let quietos = 0;
    const cuadro = () => {
      const y = window.scrollY;
      quietos = Math.abs(y - antes) < 0.5 ? quietos + 1 : 0;
      antes = y;
      const transcurrido = performance.now() - inicio;
      const llego = Math.abs(y - destino) < 1;
      // Quieto tres cuadros seguidos, pasado el arranque: el navegador lo detuvo antes (el borde).
      const detenido = quietos >= 3 && transcurrido > 150;
      if (!sigue() || llego || detenido || transcurrido > DESLIZ_MAXIMO_MS) resolver();
      else requestAnimationFrame(cuadro);
    };
    requestAnimationFrame(cuadro);
  });
}

/** Lo que se ve de la página: el alto, lo que tapan las barras fijas y cuánto se puede desplazar. */
function vistaActual(): Vista {
  const alto = window.innerHeight;
  let arriba = 0;
  let abajo = 0;
  // La cabecera pegada del celular y la barra de pestañas; el lateral de escritorio es angosto y no cuenta.
  for (const barra of Array.from(document.querySelectorAll<HTMLElement>("header, nav"))) {
    const posicion = getComputedStyle(barra).position;
    if (posicion !== "fixed" && posicion !== "sticky") continue;
    const r = barra.getBoundingClientRect();
    if (r.height === 0 || r.width < window.innerWidth / 2) continue;
    if (r.top <= 1 && r.bottom < alto / 2) arriba = Math.max(arriba, r.bottom);
    else if (r.bottom >= alto - 1 && r.top > alto / 2) abajo = Math.max(abajo, alto - r.top);
  }
  const raiz = document.scrollingElement ?? document.documentElement;
  return { alto, arriba, abajo, scroll: window.scrollY, scrollMax: raiz.scrollHeight - alto };
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
