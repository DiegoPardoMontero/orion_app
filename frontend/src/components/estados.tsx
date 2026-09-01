import { AlertCircle, WifiOff } from "lucide-react";
import type { ReactNode } from "react";
import { Constelacion } from "./marca";
import { Rigel } from "./Rigel";
import { Boton } from "./ui";

/** Cargando: la silueta de las tarjetas que vienen, con shimmer escalonado. */
export function Cargando({ filas = 3 }: { filas?: number }) {
  return (
    <div className="space-y-3" aria-busy="true" aria-live="polite">
      {Array.from({ length: filas }).map((_, i) => (
        <div
          key={i}
          className="shimmer h-28 rounded-card"
          style={{ animationDelay: `${i * 0.1}s` }}
        />
      ))}
    </div>
  );
}

/**
 * Vacío: un título que motiva y una acción. El copy alienta y nunca regaña — «cada proceso es
 * diferente; lo importante es empezar». En pantallas emocionales (estudiante/profesor) aparece
 * Rigel señalando (`mascota`); en las utilitarias (admin, tablas) va la constelación, sin mascota.
 */
export function Vacio({
  titulo,
  texto,
  accion,
  mascota = false,
}: {
  titulo: string;
  texto: string;
  accion?: ReactNode;
  mascota?: boolean;
}) {
  return (
    <div className="anim-rise flex flex-col items-center gap-3.5 rounded-card bg-surface-raised p-10 text-center shadow-sm">
      {mascota ? (
        <Rigel pose="guia" decorativo className="h-[130px] w-auto" />
      ) : (
        <div className="gradient-dawn grid h-[92px] w-[92px] place-items-center overflow-hidden rounded-card">
          <Constelacion className="h-[74px] w-[74px]" />
        </div>
      )}
      <h2 className="font-display text-[22px] font-bold text-text">{titulo}</h2>
      <p className="max-w-[340px] text-pretty text-[14px] leading-relaxed text-text-secondary">
        {texto}
      </p>
      {accion && <div className="mt-1">{accion}</div>}
    </div>
  );
}

export function ErrorCarga({
  mensaje,
  onReintentar,
}: {
  mensaje: string;
  onReintentar: () => void;
}) {
  return (
    <div className="flex flex-col items-center gap-3.5 rounded-card bg-surface-raised p-10 text-center shadow-sm">
      <div className="grid h-[60px] w-[60px] place-items-center rounded-full bg-error-bg text-error">
        <WifiOff size={24} strokeWidth={1.75} />
      </div>
      <p className="max-w-[340px] text-[14px] leading-relaxed text-text-secondary">{mensaje}</p>
      <Boton variante="contorno" onClick={onReintentar} className="h-11">
        Reintentar
      </Boton>
    </div>
  );
}

/** Banner de error del API: el mensaje viene redactado del backend y se muestra tal cual. */
export function AvisoError({ mensaje }: { mensaje: string }) {
  return (
    <p
      role="alert"
      className="flex items-start gap-2 rounded-base bg-error-bg px-4 py-3 text-[13px] font-semibold text-error"
    >
      <AlertCircle size={16} strokeWidth={1.75} className="mt-0.5 shrink-0" />
      {mensaje}
    </p>
  );
}
