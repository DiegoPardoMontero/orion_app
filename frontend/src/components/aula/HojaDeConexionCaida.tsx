"use client";

import Link from "next/link";
import { Loader2, WifiOff } from "lucide-react";
import { Boton } from "@/components/ui";
import type { ClassroomResponse } from "@/lib/api/aula";

/**
 * Se cayó la conexión. <strong>Esto no es el cierre.</strong>
 *
 * <p>La distinción es la razón de que esta hoja exista por separado: quien se queda sin internet a
 * mitad de clase no ha terminado nada, y mostrarle la pantalla de «¿cómo te fue?» le está diciendo
 * que su clase se acabó cuando todavía le quedan minutos pagados.
 *
 * <p>Por eso el tono es aviso y nunca error —no hizo nada mal—, y por eso la primera acción, la
 * coral, es volver a entrar. Terminar está, pero abajo y en texto pequeño: es una salida, no la
 * sugerencia.
 */
export function HojaDeConexionCaida({
  datos,
  minutos,
  onVolver,
  onTerminar,
}: {
  datos: ClassroomResponse;
  minutos: number;
  onVolver: () => void;
  onTerminar: () => void;
}) {
  const nombre = datos.counterpart?.firstName ?? "la otra persona";
  const esAnfitrion = datos.moderator;

  const hasta = new Date(datos.expiresAt).toLocaleTimeString("es-CO", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "America/Bogota",
  });

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-night/45 sm:items-center">
      <div
        className="w-full max-w-lg bg-surface px-6 py-6 shadow-[0_-18px_44px_rgba(51,32,59,.18)] animate-[sheet-up_380ms_cubic-bezier(.22,1,.36,1)]"
        style={{ borderRadius: "var(--sheet-radius) var(--sheet-radius) 0 0" }}
        role="dialog"
        aria-modal="true"
        aria-label="Se perdió la conexión"
      >
        <span className="inline-flex items-center gap-1.5 rounded-pill bg-warning-bg px-2.5 py-1 text-[11.5px] font-bold text-warning">
          <WifiOff size={12} strokeWidth={2.4} />
          Conexión
        </span>

        <h2 className="mt-3 font-display text-h3 font-bold">
          {esAnfitrion
            ? `A ${nombre} se le cayó la conexión.`
            : "Se perdió la conexión. La clase no terminó."}
        </h2>

        <p className="mt-1.5 text-[13px] text-text-secondary">
          {esAnfitrion
            ? `Llevaban ${minutos} min. La sala sigue abierta hasta las ${hasta}.`
            : `Llevabas ${minutos} min. La sala sigue abierta hasta las ${hasta}.`}
        </p>

        <div className="mt-4 flex items-center gap-2.5 rounded-card bg-surface-raised p-3.5">
          <Loader2 size={16} strokeWidth={2.2} className="animate-spin text-text-muted" />
          <p className="text-[13px] text-text-secondary">
            {esAnfitrion ? "Intentando reconectar…" : `${nombre} sigue en la sala.`}
          </p>
        </div>

        <div className="mt-5 flex flex-col gap-2.5">
          <Boton variante="primario" onClick={onVolver}>
            {esAnfitrion ? "Volver a la sala y esperarla" : "Volver a la sala"}
          </Boton>
          <Link
            href="/mensajes"
            className="inline-flex h-11 items-center justify-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text hover:bg-surface-sunken focus-visible:shadow-focus"
          >
            Escribirle a {nombre}
          </Link>
          <button
            type="button"
            onClick={onTerminar}
            className="rounded-base py-1 text-center text-[12.5px] text-text-muted underline focus-visible:shadow-focus"
          >
            {esAnfitrion
              ? `Dar la clase por terminada (${minutos} min)`
              : "No puedo continuar — terminar la clase"}
          </button>
        </div>
      </div>
    </div>
  );
}
