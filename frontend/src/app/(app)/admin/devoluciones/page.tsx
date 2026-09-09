"use client";

import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Boton, Campo, Spinner, Tarjeta } from "@/components/ui";
import { ApiError } from "@/lib/api/fetch";
import { fechaCorta, precioCop } from "@/lib/format";
import {
  type Devolucion,
  useConfirmarDevolucion,
  useDevolucionesPendientes,
} from "@/lib/retracto";

/**
 * Las devoluciones que hay que hacer a mano en el panel de Wompi.
 *
 * <p>Existe porque Wompi no expone reembolso por API. Todo lo demás está automatizado —detectar
 * que el retracto aplica, congelar el pago, calcular el plazo legal, avisar al estudiante y avisarte
 * a ti cuando el vencimiento se acerca—; lo único que queda es la transferencia.
 *
 * <p>Ordenadas por lo que vence antes. El plazo de 15 días calendario no es una promesa nuestra:
 * lo fija el art. 47 de la Ley 1480 con la modificación de la Ley 2439 de 2024, y pasarse es lo que
 * sanciona la SIC.
 */
export default function AdminDevolucionesPage() {
  const devoluciones = useDevolucionesPendientes();

  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-8 lg:px-8">
      <h1 className="font-display text-h1 font-bold">Devoluciones</h1>
      <p className="mt-1 text-[14px] leading-relaxed text-text-secondary">
        Dinero que hay que devolver al medio de pago original. La transferencia se hace en el panel
        de Wompi; aquí se deja la constancia.
      </p>

      {devoluciones.isPending && <Cargando filas={2} />}
      {devoluciones.isError && (
        <ErrorCarga
          mensaje="No pudimos cargar las devoluciones."
          onReintentar={() => devoluciones.refetch()}
        />
      )}
      {devoluciones.data?.length === 0 && (
        <div className="mt-5">
          <Vacio titulo="Nada por devolver" texto="No hay devoluciones pendientes ahora mismo." />
        </div>
      )}

      <div className="mt-5 grid gap-3">
        {devoluciones.data?.map((d) => (
          <FilaDevolucion key={d.id} devolucion={d} />
        ))}
      </div>
    </main>
  );
}

function FilaDevolucion({ devolucion }: { devolucion: Devolucion }) {
  const confirmar = useConfirmarDevolucion(devolucion.id);
  const [referencia, setReferencia] = useState("");
  const [nota, setNota] = useState("");

  const error = confirmar.error instanceof ApiError ? confirmar.error.message : null;

  return (
    <Tarjeta className="border border-border">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-display text-[18px] font-bold">{precioCop(devolucion.amountCop)}</p>
          <p className="mt-0.5 text-[13px] text-text-secondary">
            {devolucion.studentName} ·{" "}
            {devolucion.reason === "RETRACTO" ? "Retracto (art. 47)" : "Decisión de administración"}
          </p>
          <p className="mt-0.5 text-[12px] text-text-muted">
            Solicitada el {fechaCorta(devolucion.requestedAt)}
          </p>
        </div>
        <Badge
          tono={devolucion.overdue ? "error" : devolucion.daysLeft <= 5 ? "melocoton" : "neutral"}
        >
          {devolucion.overdue
            ? `Vencida hace ${Math.abs(devolucion.daysLeft)} d`
            : devolucion.daysLeft === 0
              ? "Vence hoy"
              : `${devolucion.daysLeft} días`}
        </Badge>
      </div>

      {devolucion.overdue && (
        <p className="mt-3 rounded-base bg-error-bg px-4 py-2.5 text-[13px] font-semibold text-error">
          El plazo legal de 15 días calendario ya venció. Devuélvelo hoy.
        </p>
      )}

      <a
        href="https://comercios.wompi.co/transactions"
        target="_blank"
        rel="noreferrer noopener"
        className="mt-3 inline-flex items-center gap-1.5 text-[13px] font-bold text-primary-strong hover:underline"
      >
        Abrir el panel de Wompi
        <ExternalLink size={14} strokeWidth={2} />
      </a>

      <div className="mt-4 border-t border-border pt-4">
        <label
          htmlFor={`ref-${devolucion.id}`}
          className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
        >
          Referencia de la devolución
        </label>
        <Campo
          id={`ref-${devolucion.id}`}
          type="text"
          maxLength={140}
          placeholder="La que te dio Wompi"
          value={referencia}
          onChange={(e) => setReferencia(e.target.value)}
          className="mt-1.5"
        />
        <Campo
          type="text"
          maxLength={2000}
          placeholder="Nota (opcional)"
          value={nota}
          onChange={(e) => setNota(e.target.value)}
          className="mt-2"
        />

        {error && (
          <div className="mt-3">
            <AvisoError mensaje={error} />
          </div>
        )}

        <Boton
          variante="primario"
          disabled={referencia.trim() === "" || confirmar.isPending}
          onClick={() =>
            confirmar.mutate({ reference: referencia.trim(), note: nota.trim() || undefined })
          }
          className="mt-3"
        >
          {confirmar.isPending ? <Spinner /> : "Marcar como devuelta"}
        </Boton>
        <p className="mt-2 text-[12px] text-text-muted">
          Sin la referencia no se puede cerrar: es la constancia de que el dinero salió.
        </p>
      </div>
    </Tarjeta>
  );
}
