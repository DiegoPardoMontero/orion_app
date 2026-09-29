"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

/**
 * Diálogo modal. En desktop es una tarjeta centrada (máx. 440 px); en móvil se presenta como
 * hoja inferior a todo el ancho, con radio solo en las esquinas superiores. Escape cierra y el
 * foco entra al panel y no sale de él con Tab — lo espera cualquiera que use teclado.
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
  // `onCerrar` suele ser una flecha nueva en cada render de quien abre el diálogo. Si el efecto
  // dependiera de ella, cada render de la página de detrás (una consulta que se refresca sola cada
  // minuto) volvería a enfocar el panel y le quitaría el foco al campo en el que alguien escribe.
  const cerrar = useRef(onCerrar);
  useEffect(() => {
    cerrar.current = onCerrar;
  });

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !bloqueante) cerrar.current();
      if (event.key === "Tab") retenerFoco(event, panel.current);
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
  }, [bloqueante]);

  // En el render del servidor no hay `document`. Todos los diálogos de la app se abren por una
  // interacción, así que esto nunca se renderiza allí; la guarda está por si algún día alguien
  // monta un modal desde el primer pintado.
  if (typeof document === "undefined") return null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-5"
      style={{ background: "rgba(51,32,59,0.45)" }}
      onClick={bloqueante ? undefined : () => cerrar.current()}
    >
      <div
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        // Con tope de alto y scroll propio: la hoja se alinea abajo, y un contenido más alto que la
        // pantalla —los filtros con sus horas— dejaba su parte de arriba fuera, sin forma de llegar.
        className={`anim-sheet max-h-[calc(100dvh-16px)] w-full overflow-y-auto overscroll-contain rounded-t-[24px] bg-surface-raised p-7 shadow-lg outline-none sm:max-h-[calc(100dvh-40px)] sm:rounded-card sm:[animation:modal-in_220ms_var(--ease-out)_both] ${amplio ? "sm:max-w-[720px]" : "sm:max-w-[440px]"}`}
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

const ENFOCABLES =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Tab da la vuelta dentro del diálogo en vez de salir a la página de detrás, que está tapada por
 * el velo: con un aviso que no se cierra, el foco terminaba en botones que nadie ve. Solo el
 * diálogo de más arriba retiene el foco, por si hay dos abiertos a la vez.
 */
function retenerFoco(event: KeyboardEvent, panel: HTMLDivElement | null) {
  if (!panel) return;
  const abiertos = document.querySelectorAll('[role="dialog"][aria-modal="true"]');
  if (abiertos[abiertos.length - 1] !== panel) return;
  const enfocables = Array.from(panel.querySelectorAll<HTMLElement>(ENFOCABLES)).filter(
    (el) => el.offsetParent !== null || el === document.activeElement,
  );
  if (enfocables.length === 0) {
    event.preventDefault();
    panel.focus();
    return;
  }
  const primero = enfocables[0];
  const ultimo = enfocables[enfocables.length - 1];
  const activo = document.activeElement;
  const dentro = activo instanceof Node && panel.contains(activo);
  if (event.shiftKey && (!dentro || activo === primero || activo === panel)) {
    event.preventDefault();
    ultimo.focus();
  } else if (!event.shiftKey && (!dentro || activo === ultimo)) {
    event.preventDefault();
    primero.focus();
  }
}
