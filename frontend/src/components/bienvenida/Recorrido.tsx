"use client";

import Link from "next/link";
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Meissa } from "@/components/Meissa";
import { Rigel } from "@/components/Rigel";
import { Boton } from "@/components/ui";
import { type Caja, type Recorrido as Definicion, ubicarTarjeta } from "@/lib/recorrido";

/** El primer elemento con ese `data-tour` que se ve de verdad: la barra lateral o la inferior. */
function buscarAncla(ancla: string | undefined): HTMLElement | null {
  if (!ancla) return null;
  const candidatos = Array.from(document.querySelectorAll<HTMLElement>(`[data-tour="${ancla}"]`));
  return (
    candidatos.find((el) => {
      const r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== "hidden";
    }) ?? null
  );
}

function cajaDe(el: HTMLElement | null): Caja | null {
  if (!el) return null;
  const r = el.getBoundingClientRect();
  const holgura = 6;
  return { top: r.top - holgura, left: r.left - holgura, width: r.width + holgura * 2, height: r.height + holgura * 2 };
}

/**
 * El recorrido guiado: oscurece la pantalla, recorta un foco sobre la parte que se explica y pone
 * al lado una tarjeta con Rigel (o Meissa, cuando el tema es la práctica).
 *
 * <p>Mientras está abierto no se puede tocar lo de atrás: el foco muestra, no invita a hacer clic.
 * Un clic en «Mis clases» a mitad del recorrido lo dejaría hablando de una pantalla que ya cambió.
 * Esc lo cierra (cuenta como «Saltar») y las flechas avanzan y retroceden.
 *
 * @param onTerminar se llama al saltarlo o al cerrarlo desde el final; los dos lo dan por visto
 */
export function Recorrido({
  definicion,
  nombre,
  onTerminar,
}: {
  definicion: Definicion;
  nombre: string;
  onTerminar: () => void;
}) {
  // -1 es la pantalla de inicio y pasos.length la de cierre.
  const [indice, setIndice] = useState(-1);
  const total = definicion.pasos.length;
  const paso = indice >= 0 && indice < total ? definicion.pasos[indice] : null;

  const [foco, setFoco] = useState<Caja | null>(null);
  const [posicion, setPosicion] = useState<{ top: number; left: number } | null>(null);
  const tarjeta = useRef<HTMLDivElement>(null);
  const tituloId = useId();

  const medir = useCallback(() => {
    const el = buscarAncla(paso?.ancla);
    const caja = cajaDe(el);
    setFoco(caja);
    const t = tarjeta.current;
    const tam = t ? { width: t.offsetWidth, height: t.offsetHeight } : { width: 340, height: 220 };
    const { top, left } = ubicarTarjeta(caja, tam, { width: window.innerWidth, height: window.innerHeight });
    setPosicion({ top, left });
  }, [paso?.ancla]);

  // Al cambiar de paso: si el ancla está fuera de la pantalla, primero se lleva a la vista. Se
  // mide en el cuadro siguiente, cuando la tarjeta ya tiene el texto nuevo y su alto real.
  useLayoutEffect(() => {
    const el = buscarAncla(paso?.ancla);
    if (el) {
      const r = el.getBoundingClientRect();
      if (r.top < 0 || r.bottom > window.innerHeight) el.scrollIntoView({ block: "center" });
    }
    const cuadro = requestAnimationFrame(medir);
    return () => cancelAnimationFrame(cuadro);
  }, [indice, medir, paso?.ancla]);

  useEffect(() => {
    window.addEventListener("resize", medir);
    window.addEventListener("scroll", medir, true);
    return () => {
      window.removeEventListener("resize", medir);
      window.removeEventListener("scroll", medir, true);
    };
  }, [medir]);

  // El foco del teclado va a la tarjeta en cada paso, para que un lector de pantalla lea el nuevo.
  useEffect(() => {
    tarjeta.current?.focus();
  }, [indice]);

  const siguiente = useCallback(() => setIndice((i) => Math.min(i + 1, total)), [total]);
  const atras = useCallback(() => setIndice((i) => Math.max(i - 1, -1)), []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onTerminar();
      else if (e.key === "ArrowRight") siguiente();
      else if (e.key === "ArrowLeft") atras();
      else if (e.key === "Tab" && tarjeta.current) {
        // El foco no sale de la tarjeta: lo de atrás está tapado.
        const enfocables = tarjeta.current.querySelectorAll<HTMLElement>("button, a[href]");
        if (enfocables.length === 0) return;
        const primero = enfocables[0];
        const ultimo = enfocables[enfocables.length - 1];
        if (e.shiftKey && document.activeElement === primero) {
          e.preventDefault();
          ultimo.focus();
        } else if (!e.shiftKey && document.activeElement === ultimo) {
          e.preventDefault();
          primero.focus();
        }
      }
    };
    document.addEventListener("keydown", onKey);
    const desborde = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = desborde;
    };
  }, [onTerminar, siguiente, atras]);

  if (typeof document === "undefined") return null;

  const enInicio = indice === -1;
  const enCierre = indice === total;
  const sombra = "0 0 0 9999px rgba(29, 20, 36, 0.62)";

  return createPortal(
    <div className="fixed inset-0 z-[60]" aria-hidden={false}>
      {/* Atrapa los clics: mientras el recorrido está abierto, lo de atrás solo se mira. */}
      <div className="absolute inset-0" style={foco ? undefined : { background: "rgba(29, 20, 36, 0.62)" }} />
      {foco && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute rounded-[18px] ring-2 ring-[#FFC189] transition-all duration-300 ease-out motion-reduce:transition-none"
          style={{ top: foco.top, left: foco.left, width: foco.width, height: foco.height, boxShadow: sombra }}
        />
      )}

      <div
        ref={tarjeta}
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        tabIndex={-1}
        className="absolute w-[min(360px,calc(100vw-32px))] rounded-card bg-surface-raised p-5 shadow-lg outline-none transition-[top,left] duration-300 ease-out motion-reduce:transition-none"
        style={posicion ?? { top: -9999, left: -9999 }}
      >
        <div className="flex items-start gap-3">
          <span className="grid h-14 w-14 shrink-0 place-items-center" aria-hidden="true">
            {paso?.guia === "meissa" ? (
              <Meissa decorativo className="h-14 w-14" />
            ) : (
              <Rigel decorativo className="h-14 w-14" pose={enCierre ? "celebracion" : enInicio ? "saludo" : "guia"} />
            )}
          </span>
          <div className="min-w-0 flex-1">
            {paso && (
              <p className="text-[11.5px] font-bold uppercase tracking-[0.1em] text-primary-strong">
                {indice + 1} de {total}
              </p>
            )}
            <h2 id={tituloId} className="mt-0.5 font-display text-[18px] font-bold leading-snug text-text">
              {enInicio ? definicion.inicio.titulo(nombre) : enCierre ? definicion.cierre.titulo : paso?.titulo}
            </h2>
            <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">
              {enInicio ? definicion.inicio.texto : enCierre ? definicion.cierre.texto : paso?.texto}
            </p>
          </div>
        </div>

        {paso && (
          <div className="mt-4 flex gap-1" aria-hidden="true">
            {definicion.pasos.map((_, i) => (
              <span key={i} className={`h-1 flex-1 rounded-pill ${i <= indice ? "bg-primary" : "bg-surface-sunken"}`} />
            ))}
          </div>
        )}

        <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
          {enCierre ? (
            <>
              <Boton variante="fantasma" onClick={onTerminar}>
                Cerrar
              </Boton>
              <Link
                href={definicion.cierre.accion.href}
                onClick={onTerminar}
                className="inline-flex min-h-11 items-center rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
              >
                {definicion.cierre.accion.etiqueta}
              </Link>
            </>
          ) : (
            <>
              <Boton variante="fantasma" onClick={onTerminar}>
                Saltar
              </Boton>
              <div className="flex gap-2">
                {!enInicio && (
                  <Boton variante="contorno" onClick={atras}>
                    Atrás
                  </Boton>
                )}
                <Boton onClick={siguiente}>{enInicio ? "Empezar" : "Siguiente"}</Boton>
              </div>
            </>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
