"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Send, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { MessageResponse } from "@/lib/api/types";
import { horaBogota } from "@/lib/format";
import { conversacionesKey, useConversaciones } from "@/lib/mensajeria";
import { ListaConversaciones } from "../lista";

/**
 * Hilo de una conversación. En desktop se ve junto a la bandeja (dos columnas); en móvil ocupa
 * toda la pantalla con una flecha para volver. Abrir el hilo marca como leídos los mensajes ajenos
 * (lo hace el propio GET del backend), así que al cargarlo refrescamos el badge de la bandeja.
 */
export default function HiloPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <main className="mx-auto w-full max-w-5xl lg:px-12 lg:py-8">
      <div className="lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-8">
        {/* La bandeja acompaña al hilo en desktop; en móvil se oculta (ya está en /mensajes). */}
        <div className="hidden lg:block">
          <ListaConversaciones activaId={id} />
        </div>
        <Hilo id={id} />
      </div>
    </main>
  );
}

function Hilo({ id }: { id: string }) {
  const queryClient = useQueryClient();
  const [texto, setTexto] = useState("");
  const finRef = useRef<HTMLDivElement>(null);
  const marcado = useRef<string | null>(null);

  const conversaciones = useConversaciones();
  const conv = conversaciones.data?.find((c) => c.id === id);
  const nombre = conv?.counterpart?.fullName ?? "Conversación";

  const mensajes = useQuery({
    queryKey: ["conversations", id, "messages"],
    queryFn: () => apiFetch<MessageResponse[]>(`/api/v1/conversations/${id}/messages`),
    refetchInterval: 15_000,
  });

  // El GET marca leídos los mensajes ajenos: cuando llega la primera respuesta, refrescamos la
  // bandeja (y su badge) una sola vez por hilo para que el contador baje.
  useEffect(() => {
    if (mensajes.isSuccess && marcado.current !== id) {
      marcado.current = id;
      void queryClient.invalidateQueries({ queryKey: conversacionesKey });
    }
  }, [mensajes.isSuccess, id, queryClient]);

  // Scroll al final cuando cambian los mensajes.
  const cantidad = mensajes.data?.length ?? 0;
  useEffect(() => {
    finRef.current?.scrollIntoView({ block: "end" });
  }, [cantidad]);

  const enviar = useMutation({
    mutationFn: (body: string) =>
      apiFetch<MessageResponse>(`/api/v1/conversations/${id}/messages`, {
        method: "POST",
        body: { body },
      }),
    onSuccess: () => {
      setTexto("");
      void mensajes.refetch();
      void queryClient.invalidateQueries({ queryKey: conversacionesKey });
    },
  });

  const errorEnvio = enviar.error instanceof ApiError ? enviar.error.message : null;

  function onSubmit() {
    const limpio = texto.trim();
    if (limpio && !enviar.isPending) {
      enviar.mutate(limpio);
    }
  }

  return (
    <section className="flex h-[calc(100dvh-160px)] flex-col lg:h-[calc(100dvh-64px)] lg:rounded-card lg:bg-surface-raised lg:shadow-sm">
      {/* Cabecera del hilo */}
      <header className="flex items-center gap-3 border-b border-surface-sunken bg-surface px-4 py-3 lg:rounded-t-card lg:bg-transparent">
        <Link
          href="/mensajes"
          aria-label="Volver a mensajes"
          className="grid h-10 w-10 shrink-0 place-items-center rounded-full text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus lg:hidden"
        >
          <ArrowLeft size={18} strokeWidth={1.75} />
        </Link>
        <Avatar nombre={nombre} fotoUrl={conv?.counterpart?.photoUrl} size="sm" />
        <div className="min-w-0">
          {/* Desde el hilo se llega a la ficha del estudiante. Solo cuando la contraparte es un
              estudiante: el perfil del profesor ya tiene el suyo, en el directorio. */}
          {conv?.counterpart?.role === "STUDENT" && conv.counterpart.id ? (
            <Link
              href={`/estudiantes/${conv.counterpart.id}`}
              className="block truncate text-[14.5px] font-bold text-text underline decoration-border underline-offset-4 transition-colors hover:decoration-primary focus-visible:shadow-focus"
            >
              {nombre}
            </Link>
          ) : (
            <p className="truncate text-[14.5px] font-bold text-text">{nombre}</p>
          )}
          <p className="text-[11.5px] text-text-muted">
            {conv?.counterpart?.role === "PROFESSOR" ? "Profesor" : "Estudiante"}
          </p>
        </div>
      </header>

      {/* Mensajes */}
      <div className="flex-1 overflow-y-auto px-4 py-4">
        {mensajes.isPending && <Cargando filas={3} />}
        {mensajes.isError && (
          <ErrorCarga
            mensaje="No pudimos cargar esta conversación."
            onReintentar={() => void mensajes.refetch()}
          />
        )}
        {mensajes.data && mensajes.data.length === 0 && (
          <p className="mx-auto max-w-xs rounded-card bg-surface-sunken px-4 py-3 text-center text-[13px] text-text-secondary">
            Aún no hay mensajes. Salúdalo y coordina tu clase por aquí.
          </p>
        )}

        <div className="flex flex-col gap-2.5">
          {mensajes.data?.map((m) => (
            <Burbuja key={m.id} mensaje={m} />
          ))}
        </div>
        <div ref={finRef} />
      </div>

      {/* Redactar */}
      <div className="border-t border-surface-sunken px-4 py-3 lg:rounded-b-card">
        {errorEnvio && (
          <p role="alert" className="mb-2 text-[12.5px] font-semibold text-error">
            {errorEnvio}
          </p>
        )}
        <div className="flex items-end gap-2">
          <textarea
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                onSubmit();
              }
            }}
            rows={1}
            maxLength={4000}
            placeholder="Escribe un mensaje…"
            aria-label="Escribe un mensaje"
            className="max-h-32 min-h-11 flex-1 resize-none rounded-[20px] border-[1.5px] border-border bg-surface-raised px-4 py-2.5 text-[14px] text-text transition-[border-color,box-shadow] placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
          />
          <button
            type="button"
            onClick={onSubmit}
            disabled={!texto.trim() || enviar.isPending}
            aria-label="Enviar mensaje"
            className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus disabled:pointer-events-none disabled:opacity-[0.42]"
          >
            {enviar.isPending ? <Spinner /> : <Send size={18} strokeWidth={1.75} />}
          </button>
        </div>
      </div>
    </section>
  );
}

function Burbuja({ mensaje }: { mensaje: MessageResponse }) {
  if (mensaje.system) {
    return (
      <p className="mx-auto max-w-sm rounded-pill bg-surface-sunken px-3.5 py-1.5 text-center text-[11.5px] text-text-muted">
        {mensaje.body}
      </p>
    );
  }

  const mio = mensaje.mine;
  const flagged = Boolean(mensaje.flaggedReason);

  return (
    <div className={`flex flex-col ${mio ? "items-end" : "items-start"}`}>
      <div
        className={`max-w-[80%] rounded-[18px] px-3.5 py-2 text-[14px] leading-relaxed ${
          mio
            ? "bg-primary text-on-primary"
            : "bg-surface-sunken text-text"
        }`}
      >
        {mensaje.body}
      </div>
      <div
        className={`mt-1 flex items-center gap-1 px-1 text-[10.5px] text-text-muted ${
          mio ? "flex-row-reverse" : ""
        }`}
      >
        <span>{mensaje.createdAt ? horaBogota(mensaje.createdAt) : ""}</span>
        {mio && mensaje.readAt && <span>· Leído</span>}
      </div>
      {flagged && (
        <p
          className={`mt-1 flex max-w-[80%] items-start gap-1.5 rounded-base bg-warning-bg px-2.5 py-1.5 text-[11px] text-warning ${
            mio ? "self-end" : "self-start"
          }`}
        >
          <ShieldCheck size={13} strokeWidth={2} className="mt-px shrink-0" />
          Por tu seguridad, Orión oculta datos de contacto; coordina todo aquí.
        </p>
      )}
    </div>
  );
}
