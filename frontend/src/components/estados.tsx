import { AlertCircle, WifiOff } from "lucide-react";
import type { ReactNode } from "react";
import { EstrellaCoral } from "./marca";
import { Boton } from "./ui";

/** Cargando: la silueta de las tarjetas que vienen, con shimmer. */
export function Cargando({ filas = 3 }: { filas?: number }) {
  return (
    <div className="space-y-3" aria-busy="true">
      {Array.from({ length: filas }).map((_, i) => (
        <div key={i} className="shimmer h-24 rounded-card" />
      ))}
    </div>
  );
}

/** Vacío: icono en círculo lavanda con su estrella, copy que motiva y nunca regaña. */
export function Vacio({
  icono,
  titulo,
  texto,
  accion,
}: {
  icono: ReactNode;
  titulo: string;
  texto: string;
  accion?: ReactNode;
}) {
  return (
    <div className="rounded-card border-[1.5px] border-border-subtle bg-surface-raised p-8 text-center">
      <div className="relative mx-auto grid h-[60px] w-[60px] place-items-center rounded-full bg-info-bg text-info">
        {icono}
        <EstrellaCoral className="estrella absolute -right-1 -top-1 h-4 w-4 text-accent" />
      </div>
      <p className="mt-4 text-[15px] font-bold">{titulo}</p>
      <p className="mt-1 text-[13px] text-text-secondary">{texto}</p>
      {accion && <div className="mt-4">{accion}</div>}
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
    <div className="rounded-card border-[1.5px] border-border-subtle bg-surface-raised p-8 text-center">
      <div className="mx-auto grid h-[60px] w-[60px] place-items-center rounded-full bg-error-bg text-error">
        <WifiOff size={24} strokeWidth={2.2} />
      </div>
      <p className="mt-4 text-[13px] text-text-secondary">{mensaje}</p>
      <Boton variante="outline" onClick={onReintentar} className="mt-4 h-11">
        Reintentar
      </Boton>
    </div>
  );
}

/** Banner de error del API: el mensaje viene redactado del backend y se muestra tal cual. */
export function AvisoError({ mensaje }: { mensaje: string }) {
  return (
    <p className="flex items-start gap-2 rounded-card bg-error-bg px-3.5 py-2.5 text-[13px] text-error-text">
      <AlertCircle size={16} strokeWidth={2.2} className="mt-0.5 shrink-0" />
      {mensaje}
    </p>
  );
}
