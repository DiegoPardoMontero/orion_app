"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

/**
 * Diálogo modal. En desktop es una tarjeta centrada (máx. 440 px); en móvil se presenta como
 * hoja inferior a todo el ancho, con radio solo en las esquinas superiores. Escape cierra y el
 * foco entra al panel — lo espera cualquiera que use teclado.
 *
 * <p>Se dibuja en un <strong>portal a {@code document.body}</strong>, y eso no es un detalle de
 * implementación: es lo que hace que el modal funcione. Un `z-index` solo compite dentro de su
 * contexto de apilamiento, y quien abre este diálogo suele estar dentro de uno —la cabecera móvil
 * es `sticky z-30`, y un `sticky` con `z-index` crea contexto—. Ahí dentro, `z-50` no gana nada:
 * la barra inferior, que también es `z-30` pero va después en el DOM, se dibuja encima del modal.
 * Subir el número no arregla eso; salir del contexto, sí.
 */
export function Modal({
  titulo,
  onCerrar,
  bloqueante = false,
  amplio = false,
  children,
}: {
  titulo: string;
  onCerrar: () => void;
  /**
   * Un diálogo que no se descarta: ni con Escape ni pulsando fuera. Solo para lo que se responde
   * en vez de leerse — hoy, la declaración de mayoría de edad. Un aviso que se cierra sin querer
   * es un aviso que nadie contestó, y aquí lo que se pide es una declaración.
   */
  bloqueante?: boolean;
  /** Más ancho en escritorio (720 px), para lo que no cabe en una tarjeta de aviso: un video. */
  amplio?: boolean;
  children: ReactNode;
}) {
  const panel = useRef<HTMLDivElement>(null);
  const tituloId = useId();

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !bloqueante) onCerrar();
    };
    document.addEventListener("keydown", onKey);
    panel.current?.focus();
    // Mientras el diálogo está abierto, la página de detrás no se desplaza: en móvil, arrastrar
    // sobre el fondo movía la página y dejaba la hoja a medio camino.
    const desbordeAnterior = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = desbordeAnterior;
    };
  }, [onCerrar, bloqueante]);

  // En el render del servidor no hay `document`. Todos los diálogos de la app se abren por una
  // interacción, así que esto nunca se renderiza allí; la guarda está por si algún día alguien
  // monta un modal desde el primer pintado.
  if (typeof document === "undefined") return null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-5"
      style={{ background: "rgba(51,32,59,0.45)" }}
      onClick={bloqueante ? undefined : onCerrar}
    >
      <div
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        className={`anim-sheet w-full rounded-t-[24px] bg-surface-raised p-7 shadow-lg outline-none sm:rounded-card sm:[animation:modal-in_220ms_var(--ease-out)_both] ${amplio ? "sm:max-w-[720px]" : "sm:max-w-[440px]"}`}
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id={tituloId} className="font-display text-[22px] font-bold text-text">
          {titulo}
        </h2>
        <div className="mt-3">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
