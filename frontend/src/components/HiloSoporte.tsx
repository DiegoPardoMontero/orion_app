"use client";

import { ArrowLeft, CheckCheck } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Badge, Boton, Spinner, Tarjeta } from "@/components/ui";
import { ApiError } from "@/lib/api/fetch";
import { fechaRelativa } from "@/lib/format";
import {
  ETIQUETA_ESTADO,
  TONO_ESTADO,
  diasParaVencer,
  useCerrarSolicitud,
  useHilo,
  useResponder,
} from "@/lib/soporte";

/**
 * Un hilo de soporte. La misma pantalla para las dos partes: cambia quién puede cerrar y a dónde
 * lleva el «volver». Dos versiones separadas se habrían desincronizado en la tercera corrección.
 */
export function HiloSoporte({ code, esAdmin = false }: { code: string; esAdmin?: boolean }) {
  const hilo = useHilo(code, esAdmin);
  const responder = useResponder(code, esAdmin);
  const cerrar = useCerrarSolicitud(code);
  const [texto, setTexto] = useState("");

  const error =
    responder.error instanceof ApiError
      ? responder.error.message
      : cerrar.error instanceof ApiError
        ? cerrar.error.message
        : null;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (texto.trim() === "") return;
    responder.mutate(texto.trim(), { onSuccess: () => setTexto("") });
  }

  if (hilo.isPending) return <Cargando filas={3} />;
  if (hilo.isError) {
    return (
      <ErrorCarga
        mensaje="No encontramos esa solicitud."
        onReintentar={() => hilo.refetch()}
      />
    );
  }

  const { ticket, messages } = hilo.data;
  const dias = ticket.dueAt ? diasParaVencer(ticket.dueAt) : null;

  return (
    <div>
      <Link
        href={esAdmin ? "/admin/soporte" : "/ayuda"}
        className="inline-flex items-center gap-1.5 text-[13px] font-bold text-text-secondary hover:text-text"
      >
        <ArrowLeft size={16} strokeWidth={2} />
        {esAdmin ? "Bandeja" : "Ayuda"}
      </Link>

      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="font-display text-[24px] font-bold leading-tight">{ticket.subject}</h1>
          <p className="mt-1 text-[12.5px] text-text-muted">
            <span className="mono">{ticket.code}</span> · {ticket.categoryLabel} ·{" "}
            {fechaRelativa(ticket.createdAt)}
          </p>
        </div>
        <Badge tono={TONO_ESTADO[ticket.status]}>{ETIQUETA_ESTADO[ticket.status]}</Badge>
      </div>

      {/* El vencimiento solo se enseña donde sirve para actuar: en la bandeja de quien responde.
          A quien escribió le diría poco y le sonaría a cuenta atrás en su contra. */}
      {esAdmin && ticket.dueAt && dias !== null && (
        <p
          className={`mt-3 rounded-base px-4 py-2.5 text-[13px] font-semibold ${
            ticket.overdue
              ? "bg-error-bg text-error"
              : dias <= 2
                ? "bg-warning-bg text-warning"
                : "bg-surface-sunken text-text-secondary"
          }`}
        >
          {ticket.overdue
            ? `Plazo legal vencido hace ${Math.abs(dias)} día${Math.abs(dias) === 1 ? "" : "s"}.`
            : dias === 0
              ? "El plazo legal vence hoy."
              : `Quedan ${dias} día${dias === 1 ? "" : "s"} de plazo legal.`}
        </p>
      )}

      <div className="mt-5 grid gap-3">
        {messages.map((m, i) => (
          <Tarjeta
            key={i}
            className={m.mine ? "border border-border" : "border border-border bg-surface-sunken"}
          >
            <p className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-muted">
              {m.mine ? "Tú" : esAdmin ? "La persona" : "Orión"} · {fechaRelativa(m.createdAt)}
            </p>
            <p className="mt-2 whitespace-pre-wrap text-[15px] leading-relaxed text-text-secondary">
              {m.body}
            </p>
          </Tarjeta>
        ))}
      </div>

      <form onSubmit={onSubmit} className="mt-5">
        <label htmlFor="respuesta" className="sr-only">
          Escribe tu respuesta
        </label>
        <textarea
          id="respuesta"
          rows={4}
          maxLength={4000}
          placeholder={
            ticket.status === "CLOSED"
              ? "Esta solicitud está cerrada. Si escribes, se vuelve a abrir."
              : "Escribe tu respuesta"
          }
          value={texto}
          onChange={(event) => setTexto(event.target.value)}
          className="w-full rounded-base border border-border bg-surface p-3 text-[15px] leading-relaxed focus-visible:shadow-focus"
        />

        {error && (
          <div className="mt-3">
            <AvisoError mensaje={error} />
          </div>
        )}

        <div className="mt-3 flex flex-wrap gap-2">
          <Boton
            variante="primario"
            type="submit"
            disabled={texto.trim() === "" || responder.isPending}
          >
            {responder.isPending ? (
              <>
                <Spinner />
                Enviando…
              </>
            ) : (
              "Responder"
            )}
          </Boton>

          {esAdmin && ticket.status !== "CLOSED" && (
            <Boton
              variante="contorno"
              type="button"
              onClick={() => cerrar.mutate()}
              disabled={cerrar.isPending}
            >
              <CheckCheck size={16} strokeWidth={2} />
              Cerrar solicitud
            </Boton>
          )}
        </div>
      </form>
    </div>
  );
}
