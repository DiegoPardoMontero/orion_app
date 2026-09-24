"use client";

import { ChevronRight, Play, RotateCcw, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useBienvenida } from "@/lib/bienvenida";
import { abrirRecorrido } from "@/lib/recorrido";
import { VideoBienvenida } from "./VideoBienvenida";

/**
 * Ayuda (handoff §7, captura 03): la bienvenida de Sofía —si al profesor le tocó y hay video—, el
 * recorrido para repetirlo desde el paso 1, las preguntas frecuentes y escribirle a soporte.
 */
export function ConoceOrion({
  rol,
  onPreguntas,
  onSoporte,
}: {
  rol: "PROFESSOR" | "STUDENT" | null;
  onPreguntas: () => void;
  onSoporte: () => void;
}) {
  const bienvenida = useBienvenida(rol !== null);
  const [viendo, setViendo] = useState(false);
  const video = bienvenida.data?.welcomeVideo;

  const fila = "flex min-h-16 w-full items-center gap-3.5 px-4 py-3.5 text-left transition-colors hover:bg-[#FFF6EE] focus-visible:shadow-[inset_0_0_0_3px_rgba(232,80,58,.35)] focus-visible:outline-none";
  const filaSimple = "flex h-14 w-full items-center gap-3.5 px-4 text-left text-[15px] font-semibold text-[#33203B] transition-colors hover:bg-[#FFF6EE] focus-visible:shadow-[inset_0_0_0_3px_rgba(232,80,58,.35)] focus-visible:outline-none";

  return (
    <div className="mt-5 flex flex-col gap-3.5">
      {rol && (
        <div className="overflow-hidden rounded-[22px] bg-white shadow-[0_1px_2px_rgba(51,32,59,.06)]">
          {video && (
            <button type="button" onClick={() => setViendo(true)} className={fila}>
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-[14px] bg-[#FDE4E0] text-[#C93A26]">
                <Play size={20} strokeWidth={1.75} />
              </span>
              <span className="flex flex-1 flex-col gap-0.5">
                <span className="text-[15px] font-bold text-[#33203B]">Ver la bienvenida de Sofía</span>
                <span className="text-[13px] text-[#7A6B85]">Video · 2 min</span>
              </span>
              <ChevronRight size={18} strokeWidth={1.75} className="text-[#5E4E6B]" aria-hidden />
            </button>
          )}
          <button
            type="button"
            onClick={() => abrirRecorrido(rol === "PROFESSOR" ? "TOUR_PROFESSOR" : "TOUR_STUDENT", "paso1")}
            className={`${fila} ${video ? "border-t border-[#F4EAE0]" : ""}`}
          >
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-[14px] bg-[#FFF1C9] text-[#7A5A12]">
              <RotateCcw size={20} strokeWidth={1.75} />
            </span>
            <span className="flex flex-1 flex-col gap-0.5">
              <span className="text-[15px] font-bold text-[#33203B]">Repetir el recorrido</span>
              <span className="text-[13px] text-[#7A6B85]">{rol === "PROFESSOR" ? "8 pasos con Rigel" : "6 pasos con Rigel"}</span>
            </span>
            <ChevronRight size={18} strokeWidth={1.75} className="text-[#5E4E6B]" aria-hidden />
          </button>
        </div>
      )}

      <div className="overflow-hidden rounded-[22px] bg-white shadow-[0_1px_2px_rgba(51,32,59,.06)]">
        <button type="button" onClick={onPreguntas} className={filaSimple}>
          <span className="flex-1">Preguntas frecuentes</span>
          <ChevronRight size={18} strokeWidth={1.75} className="text-[#5E4E6B]" aria-hidden />
        </button>
        <button type="button" onClick={onSoporte} className={`${filaSimple} border-t border-[#F4EAE0]`}>
          <span className="flex-1">Escribirle a soporte</span>
          <ChevronRight size={18} strokeWidth={1.75} className="text-[#5E4E6B]" aria-hidden />
        </button>
      </div>

      {video && viendo && <VideoEnModal url={video.url} onCerrar={() => setViendo(false)} />}
    </div>
  );
}

/**
 * La bienvenida desde Ayuda (captura 04): en escritorio, un modal de 880 con un solo botón
 * «Cerrar»; en móvil, a pantalla completa. No relanza el recorrido.
 */
function VideoEnModal({ url, onCerrar }: { url: string; onCerrar: () => void }) {
  const tituloId = useId();
  const cerrar = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    cerrar.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCerrar();
    };
    document.addEventListener("keydown", onKey);
    const antes = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = antes;
    };
  }, [onCerrar]);

  return createPortal(
    <div className="fixed inset-0 z-[90] flex items-center justify-center bg-[#FFF6EE] sm:bg-[rgba(46,30,78,.74)] sm:p-6">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        className="anim-modal flex h-full w-full flex-col gap-4 bg-white p-5 sm:h-auto sm:max-w-[880px] sm:rounded-[22px] sm:shadow-[0_18px_44px_rgba(46,30,78,.35)]"
      >
        <div className="flex items-center justify-between">
          <h2 id={tituloId} className="pl-1 font-display text-[20px] font-bold text-[#33203B]">
            La bienvenida de Sofía
          </h2>
          <button
            ref={cerrar}
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="grid h-11 w-11 place-items-center rounded-full bg-[#F4EAE0] text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none"
          >
            <X size={18} strokeWidth={2} />
          </button>
        </div>
        <div className="flex flex-1 items-center sm:block">
          <VideoBienvenida url={url} grande autoplay />
        </div>
      </div>
    </div>,
    document.body,
  );
}
