"use client";

import Link from "next/link";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import type { ConversationSummary } from "@/lib/api/types";
import { diaBogota, fechaCorta, horaBogota } from "@/lib/format";
import { useConversaciones } from "@/lib/mensajeria";

/** Hora si el mensaje es de hoy; si no, la fecha corta. Igual criterio que un chat cualquiera. */
function cuando(iso: string | undefined): string {
  if (!iso) return "";
  return diaBogota(iso) === diaBogota(new Date().toISOString())
    ? horaBogota(iso)
    : fechaCorta(iso);
}

/** Extracto del último mensaje: "Tú: …", los de sistema en cursiva, o una invitación si está vacío. */
function extracto(conv: ConversationSummary): { texto: string; sistema: boolean } {
  const ultimo = conv.lastMessage;
  if (!ultimo?.body) {
    return { texto: "Escribe el primer mensaje", sistema: true };
  }
  if (ultimo.system) {
    return { texto: ultimo.body, sistema: true };
  }
  return { texto: ultimo.mine ? `Tú: ${ultimo.body}` : ultimo.body, sistema: false };
}

/**
 * La bandeja de conversaciones, compartida por `/mensajes` (columna izquierda o pantalla completa
 * en móvil) y `/mensajes/[id]` (columna izquierda en desktop, con el hilo activo resaltado).
 */
export function ListaConversaciones({ activaId }: { activaId?: string }) {
  const { data, isPending, isError, refetch } = useConversaciones();

  if (isPending) {
    return <Cargando filas={4} />;
  }

  if (isError) {
    return (
      <ErrorCarga
        mensaje="No pudimos cargar tus mensajes."
        onReintentar={() => void refetch()}
      />
    );
  }

  if (data.length === 0) {
    return (
      <Vacio
        mascota
        titulo="Aún no tienes mensajes"
        texto="Cuando escribas a un profesor —o un estudiante te escriba— la conversación aparecerá aquí. Todo se coordina dentro de Orión."
      />
    );
  }

  return (
    <ul className="flex flex-col gap-1.5">
      {data.map((conv) => {
        const activa = conv.id === activaId;
        const nombre = conv.counterpart?.fullName ?? "Conversación";
        const { texto, sistema } = extracto(conv);
        const noLeidos = conv.unreadCount ?? 0;

        return (
          <li key={conv.id}>
            <Link
              href={`/mensajes/${conv.id}`}
              aria-current={activa ? "page" : undefined}
              className={`flex items-center gap-3 rounded-card p-3 transition-colors ${
                activa
                  ? "bg-primary-soft"
                  : "bg-surface-raised shadow-sm hover:bg-surface-sunken"
              }`}
            >
              <Avatar nombre={nombre} fotoUrl={conv.counterpart?.photoUrl} size="md" />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2">
                  <span className="truncate text-[14px] font-bold text-text">{nombre}</span>
                  <span className="shrink-0 text-[11.5px] text-text-muted">
                    {cuando(conv.lastMessageAt)}
                  </span>
                </div>
                <div className="mt-0.5 flex items-center justify-between gap-2">
                  <span
                    className={`truncate text-[12.5px] ${
                      noLeidos > 0
                        ? "font-semibold text-text"
                        : sistema
                          ? "italic text-text-muted"
                          : "text-text-secondary"
                    }`}
                  >
                    {texto}
                  </span>
                  {noLeidos > 0 && (
                    <span className="grid h-[18px] min-w-[18px] shrink-0 place-items-center rounded-pill bg-primary px-1.5 text-[11px] font-bold text-on-primary">
                      {noLeidos > 9 ? "9+" : noLeidos}
                    </span>
                  )}
                </div>
              </div>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
