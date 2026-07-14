import type { ReactNode } from "react";

/** Cargando: bloques neutros con la forma aproximada del contenido que viene. */
export function Cargando({ filas = 3 }: { filas?: number }) {
  return (
    <div className="space-y-3" aria-busy="true">
      {Array.from({ length: filas }).map((_, i) => (
        <div key={i} className="h-20 animate-pulse rounded-card border border-line bg-card" />
      ))}
    </div>
  );
}

/** Vacío: la voz de marca motiva, nunca regaña. */
export function Vacio({ titulo, texto, accion }: { titulo: string; texto: string; accion?: ReactNode }) {
  return (
    <div className="rounded-card border border-line bg-card p-8 text-center">
      <p className="font-semibold">{titulo}</p>
      <p className="mt-1 text-sm text-ink-soft">{texto}</p>
      {accion && <div className="mt-4">{accion}</div>}
    </div>
  );
}

export function ErrorCarga({ mensaje, onReintentar }: { mensaje: string; onReintentar: () => void }) {
  return (
    <div className="rounded-card border border-line bg-card p-6 text-center">
      <p className="text-sm text-danger">{mensaje}</p>
      <button
        type="button"
        onClick={onReintentar}
        className="mt-3 rounded-orion border border-line px-3 py-1.5 text-sm text-ink-soft"
      >
        Reintentar
      </button>
    </div>
  );
}
