"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

/**
 * Diálogo modal. En desktop es una tarjeta centrada (máx. 440 px); en móvil se presenta como
 * hoja inferior a todo el ancho, con radio solo en las esquinas superiores. Escape cierra y el
 * foco entra al panel — lo espera cualquiera que use teclado.
 */
export function Modal({
  titulo,
  onCerrar,
  children,
}: {
  titulo: string;
  onCerrar: () => void;
  children: ReactNode;
}) {
  const panel = useRef<HTMLDivElement>(null);
  const tituloId = useId();

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCerrar();
    };
    document.addEventListener("keydown", onKey);
    panel.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [onCerrar]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-5"
      style={{ background: "rgba(51,32,59,0.45)" }}
      onClick={onCerrar}
    >
      <div
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        className="anim-sheet w-full rounded-t-[24px] bg-surface-raised p-7 shadow-lg outline-none sm:max-w-[440px] sm:rounded-card sm:[animation:modal-in_220ms_var(--ease-out)_both]"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id={tituloId} className="font-display text-[22px] font-bold text-text">
          {titulo}
        </h2>
        <div className="mt-3">{children}</div>
      </div>
    </div>
  );
}
